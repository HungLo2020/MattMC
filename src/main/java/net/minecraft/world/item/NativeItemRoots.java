package net.minecraft.world.item;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.NativeLibraryLoader;

/** Item-private VM reachability resources; actual levels and membership live in Rust. */
final class NativeItemRoots {
    private NativeItemRoots() {}
    private static final Cleaner CLEANER = Cleaner.create();
    private static final MethodHandle NEW = call("enchantments_create", 1);
    private static final MethodHandle TABLE = call("enchantments_table", 1);
    private static final MethodHandle TABLE_ADDRESS = call("enchantments_table_address", 1);
    private static final MethodHandle TABLE_RETAIN = call("enchantments_table_retain", 1);
    private static final MethodHandle TABLE_CONTAINS = call("enchantments_table_contains", 2);
    private static final MethodHandle TABLE_READ = call("enchantments_table_read", 3);
    private static final MethodHandle CURSOR_NEW = call("enchantments_cursor_create", 1);
    private static final MethodHandle SPLIT_NEW = call("enchantments_split_create", 1), SPLIT_PREPARE = call("enchantments_split_prepare", 1), SPLIT_COMMIT = call("enchantments_split_commit", 2);
    private static final MethodHandle SPLIT_OP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_enchantments_split_op",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle QUERY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_enchantments_query",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle ENTRY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_enchantments_table_entry",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle CURSOR_OP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_enchantments_cursor_op",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle SIZE = call("enchantments_size", 1);
    private static final MethodHandle ADDRESS = call("enchantments_owner_address", 1);
    private static final MethodHandle RELEASE = call("owner_release", 1);
    private static final MethodHandle GET = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_enchantments_get",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle PUT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_enchantments_put",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle REMOVE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_enchantments_remove",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

    private static MethodHandle call(String name, int arguments) {
        var layouts = new java.lang.foreign.MemoryLayout[arguments];
        java.util.Arrays.fill(layouts, ValueLayout.JAVA_LONG);
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_" + name,
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, layouts));
    }
    private static long invoke(MethodHandle method, long argument) {
        try { return (long)method.invokeExact(argument); }
        catch (Throwable failure) { return rethrow(failure); }
    }
    private static long invoke(MethodHandle method, long first, long second) {
        try { return (long)method.invokeExact(first, second); }
        catch (Throwable failure) { return rethrow(failure); }
    }
    private static long invoke(MethodHandle method, long first, long second, long third) {
        try { return (long)method.invokeExact(first, second, third); }
        catch (Throwable failure) { return rethrow(failure); }
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> long rethrow(Throwable failure) throws E { throw (E)failure; }

    /** Cleanup contains no owner or VM-anchor reference. Registration precedes acquisition. */
    private static final class Release implements Runnable {
        private final AtomicLong handle = new AtomicLong();
        public void run() {
            long acquired = handle.getAndSet(0);
            if (acquired != 0) invoke(RELEASE, acquired);
        }
    }
    private static class Lease {
        private final Release release = new Release();
        private final Cleaner.Cleanable cleanup = CLEANER.register(this, release);
        final void acquire(long handle) {
            if (handle <= 0) throw new IllegalStateException("Cannot acquire native enchantment resource: " + handle);
            if (!release.handle.compareAndSet(0, handle)) {
                invoke(RELEASE, handle);
                throw new IllegalStateException("Native enchantment resource already acquired");
            }
        }
        final long handle() {
            long handle = release.handle.get();
            if (handle == 0) throw new IllegalStateException("Native enchantment resource is closed");
            return handle;
        }
        final void close() { cleanup.clean(); }
    }

    /** This resource retains the actual table allocation, including escaped old arrays. */
    private static final class TableRoots extends Lease {
        final Long2ObjectOpenHashMap<Object> anchors = new Long2ObjectOpenHashMap<>() {
            @Override protected void rehash(int next) { if (next >= n) super.rehash(next); }
        };
        final long address;
        TableRoots(long owner) {
            acquire(invoke(TABLE, owner));
            address = invoke(TABLE_ADDRESS, handle());
        }
        TableRoots(long address, boolean retain) {
            acquire(invoke(TABLE_RETAIN, address));
            this.address = address;
        }
        Object resolve(long token) {
            if (token == 0) return null;
            Object value = anchors.get(token);
            if (value == null) throw new IllegalStateException("Missing enchantment holder anchor " + token);
            return value;
        }
    }

    static final class Enchantments extends AbstractObject2IntMap<Object> {
        private final Release release = new Release();
        private final Cleaner.Cleanable cleanup = CLEANER.register(this, release);
        private TableRoots roots;
        private long nextToken = 1;
        private ObjectSet<Object> keys;
        private Object2IntMap.FastEntrySet<Object> entries;
        private void acquire(long handle) {
            if (handle <= 0) throw new IllegalStateException("Cannot acquire native enchantment resource: " + handle);
            if (!release.handle.compareAndSet(0, handle)) { invoke(RELEASE, handle); throw new IllegalStateException("Native enchantments already acquired"); }
        }
        private long handle() {
            long handle = release.handle.get();
            if (handle == 0) throw new IllegalStateException("Native enchantment resource is closed");
            return handle;
        }
        private void close() { cleanup.clean(); }
        long address() {
            try {
                long address = invoke(ADDRESS, handle());
                if (address <= 0 || (address & 3) != 0) throw new IllegalStateException("Invalid native enchantment allocation");
                return address;
            } finally { Reference.reachabilityFence(this); }
        }
        Enchantments(int expected) {
            acquire(invoke(NEW, expected));
            try { roots = new TableRoots(handle()); }
            catch (Throwable failure) { close(); throw failure; }
        }
        @Override public int size() {
            try { return Math.toIntExact(invoke(SIZE, handle())); }
            finally { Reference.reachabilityFence(this); }
        }
        Object keyAt(int index) {
            TableRoots selected = roots;
            try {
                long token = invoke(TABLE_READ, selected.handle(), index, 0);
                if (token == Long.MIN_VALUE) throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + invoke(TABLE_READ, selected.handle(), 0, 2));
                return selected.resolve(token);
            } finally { Reference.reachabilityFence(selected); Reference.reachabilityFence(this); }
        }
        KeyCursor keyIterator() { return new KeyCursor(this, 0); }
        @SuppressWarnings("unchecked") private ObjectIterator<Object2IntMap.Entry<Object>> entryIterator(boolean fast) {
            return (ObjectIterator<Object2IntMap.Entry<Object>>)(ObjectIterator<?>)new KeyCursor(this, fast ? 2 : 1);
        }
        @Override public int getInt(Object key) { return get(key, 0, false); }
        @Override public int getOrDefault(Object key, int missing) { return get(key, missing, true); }
        @Override public boolean containsKey(Object key) { return query(key, 0, 0, null) != 0; }
        @Override public boolean containsValue(int level) { return query(null, 1, level, null) != 0; }
        @Override public int defaultReturnValue() { return (int)query(null, 4, 0, null); }
        @Override public void defaultReturnValue(int level) { query(null, 5, level, null); }
        @Override public boolean isEmpty() { return size() == 0; }
        @Override public void clear() { if (query(null, 6, 0, null) != 0) roots.anchors.clear(); }
        @Override public int hashCode() { return (int)query(null, 9, 0, null); }
        @Override public void putAll(java.util.Map<? extends Object, ? extends Integer> source) {
            query(null, 7, size() + source.size(), null); super.putAll(source);
        }
        int upgrade(Object key, int level) { return (int)query(key, 8, level, null); }
        private long query(Object key, long operation, long argument, java.util.function.IntConsumer consumer) {
            Effects previous = EFFECTS.get(); Effects effects = new Effects(this, roots, key); effects.consumer = consumer; EFFECTS.set(effects);
            try {
                long result = (long)QUERY.invokeExact(handle(), operation, key == null ? 0L : -1L, argument, Callback.STUB);
                if (effects.failure != null) return rethrow(effects.failure);
                if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalStateException("Native enchantment command failed: " + result);
                return result;
            } catch (Throwable failure) { return rethrow(failure); }
            finally { try { effects.finish(); } finally {
                if (previous == null) EFFECTS.remove(); else EFFECTS.set(previous);
                Reference.reachabilityFence(effects); Reference.reachabilityFence(this);
            } }
        }
        private int entryLevel(int index, int level, boolean write) {
            TableRoots selected = roots; Effects previous = EFFECTS.get(); Effects effects = new Effects(this, selected, null); EFFECTS.set(effects);
            try {
                long result = (long)ENTRY.invokeExact(selected.handle(), (long)index, (long)level, write ? 1L : 0L, Callback.STUB);
                if (effects.failure != null) return (int)rethrow(effects.failure);
                if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalStateException("Native enchantment entry failed: " + result);
                return (int)result;
            } catch (Throwable failure) { return (int)rethrow(failure); }
            finally {
                if (previous == null) EFFECTS.remove(); else EFFECTS.set(previous);
                Reference.reachabilityFence(selected); Reference.reachabilityFence(this);
            }
        }
        @Override public ObjectSet<Object> keySet() {
            if (keys == null) keys = new AbstractObjectSet<>() {
                public int size() { return Enchantments.this.size(); }
                public boolean contains(Object key) { return containsKey(key); }
                public boolean remove(Object key) { int old = size(); removeInt(key); return size() != old; }
                public void clear() { Enchantments.this.clear(); }
                public ObjectIterator<Object> iterator() { return keyIterator(); }
                public it.unimi.dsi.fastutil.objects.ObjectSpliterator<Object> spliterator() { return new Split(Enchantments.this, false); }
                public void forEach(java.util.function.Consumer<? super Object> action) {
                    query(null, 10, 0, index -> action.accept(keyAt(index)));
                }
            };
            return keys;
        }
        @Override public Object2IntMap.FastEntrySet<Object> object2IntEntrySet() {
            if (entries == null) entries = new EntrySet(this);
            return entries;
        }
        int get(Object query, int missing, boolean useMissing) {
            Effects previous = EFFECTS.get();
            Effects effects = new Effects(this, roots, query);
            EFFECTS.set(effects);
            try {
                long result = (long)GET.invokeExact(handle(), query == null ? 0L : -1L,
                    (long)missing, useMissing ? 1L : 0L, Callback.STUB);
                if (effects.failure != null) return (int)rethrow(effects.failure);
                if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE)
                    throw new IllegalStateException("Native enchantment lookup failed: " + result);
                return (int)result;
            } catch (Throwable failure) { return (int)rethrow(failure); }
            finally {
                if (previous == null) EFFECTS.remove(); else EFFECTS.set(previous);
                Reference.reachabilityFence(effects);
                Reference.reachabilityFence(this);
            }
        }
        @Override public int put(Object query, int level) {
            return mutate(PUT, query, level);
        }
        @Override public int removeInt(Object query) {
            return mutate(REMOVE, query, 0);
        }
        private int mutate(MethodHandle operation, Object query, int level) {
            Effects previous = EFFECTS.get();
            Effects effects = new Effects(this, roots, query);
            EFFECTS.set(effects);
            try {
                long result = (long)operation.invokeExact(handle(), query == null ? 0L : -1L, (long)level, Callback.STUB);
                if (effects.failure != null) return (int)rethrow(effects.failure);
                if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE)
                    throw new IllegalStateException("Native enchantment mutation failed: " + result);
                return (int)result;
            } catch (Throwable failure) { return (int)rethrow(failure); }
            finally {
                try { effects.finish(); }
                finally {
                    if (previous == null) EFFECTS.remove(); else EFFECTS.set(previous);
                    Reference.reachabilityFence(effects);
                    Reference.reachabilityFence(this);
                }
            }
        }
    }
    private static final class EntrySet extends AbstractObjectSet<Object2IntMap.Entry<Object>> implements Object2IntMap.FastEntrySet<Object> {
        final Enchantments owner;
        EntrySet(Enchantments owner) { this.owner = owner; }
        public int size() { return owner.size(); }
        public void clear() { owner.clear(); }
        public ObjectIterator<Object2IntMap.Entry<Object>> iterator() { return owner.entryIterator(false); }
        public ObjectIterator<Object2IntMap.Entry<Object>> fastIterator() { return owner.entryIterator(true); }
        @SuppressWarnings("unchecked") public it.unimi.dsi.fastutil.objects.ObjectSpliterator<Object2IntMap.Entry<Object>> spliterator() {
            return (it.unimi.dsi.fastutil.objects.ObjectSpliterator<Object2IntMap.Entry<Object>>)(it.unimi.dsi.fastutil.objects.ObjectSpliterator<?>)new Split(owner, true);
        }
        public boolean contains(Object value) { return match(value, 11); }
        public boolean remove(Object value) { return match(value, 12); }
        private boolean match(Object value, int operation) {
            if (!(value instanceof java.util.Map.Entry<?, ?> entry)) return false;
            if (entry.getValue() == null || !(entry.getValue() instanceof Integer)) return false;
            Object key = entry.getKey(); int level = (Integer)entry.getValue();
            return owner.query(key, operation, level, null) != 0;
        }
        public void forEach(java.util.function.Consumer<? super Object2IntMap.Entry<Object>> consumer) {
            owner.query(null, 10, 0, index -> consumer.accept(new Entry(owner, index)));
        }
        public void fastForEach(java.util.function.Consumer<? super Object2IntMap.Entry<Object>> consumer) {
            Entry entry = new Entry(owner, 0);
            owner.query(null, 10, 0, index -> { entry.index = index; consumer.accept(entry); });
        }
    }
    private static final class Entry implements Object2IntMap.Entry<Object>, it.unimi.dsi.fastutil.objects.ObjectIntPair<Object> {
        final Enchantments owner; int index;
        Entry(Enchantments owner, int index) { this.owner = owner; this.index = index; }
        public Object getKey() { return owner.keyAt(index); }
        public Object left() { return getKey(); }
        public int getIntValue() { return owner.entryLevel(index, 0, false); }
        public int rightInt() { return getIntValue(); }
        public int setValue(int level) { return owner.entryLevel(index, level, true); }
        public it.unimi.dsi.fastutil.objects.ObjectIntPair<Object> right(int level) { setValue(level); return this; }
        public Integer getValue() { return getIntValue(); }
        public Integer setValue(Integer level) { return setValue(level.intValue()); }
        public boolean equals(Object value) {
            return value instanceof java.util.Map.Entry<?, ?> other && java.util.Objects.equals(getKey(), other.getKey()) && getIntValue() == (Integer)other.getValue();
        }
        public int hashCode() { return (getKey() == null ? 0 : getKey().hashCode()) ^ getIntValue(); }
        public String toString() { return new StringBuilder().append(getKey()).append("=>").append(getIntValue()).toString(); }
    }
    private static final class Split extends Lease implements it.unimi.dsi.fastutil.objects.ObjectSpliterator<Object> {
        final Enchantments owner; final boolean entries;
        Split(Enchantments owner, boolean entries) {
            this.owner = owner; this.entries = entries;
            try { acquire(invoke(SPLIT_NEW, owner.handle())); } finally { Reference.reachabilityFence(owner); }
        }
        Split(Split parent) {
            owner = parent.owner; entries = parent.entries;
            try { acquire(invoke(SPLIT_PREPARE, parent.handle())); } finally { Reference.reachabilityFence(parent); }
        }
        private long raw(long operation, long argument) {
            try { return (long)SPLIT_OP.invokeExact(handle(), operation, argument, Callback.STUB); }
            catch (Throwable failure) { return rethrow(failure); }
            finally { Reference.reachabilityFence(this); }
        }
        public long estimateSize() { return raw(0, 0); }
        public int characteristics() { return (int)raw(1, 0); }
        public Split trySplit() {
            if (raw(2, 0) == 0) return null;
            Split prepared = new Split(this);
            try {
                if (invoke(SPLIT_COMMIT, handle(), prepared.handle()) != 1) throw new IllegalStateException("Stale native enchantment split");
                return prepared;
            } catch (Throwable failure) { prepared.close(); throw failure; }
            finally { Reference.reachabilityFence(prepared); Reference.reachabilityFence(this); }
        }
        public boolean tryAdvance(java.util.function.Consumer<? super Object> consumer) { return run(3, 0, consumer) != 0; }
        public void forEachRemaining(java.util.function.Consumer<? super Object> consumer) { run(4, 0, consumer); }
        public long skip(long count) {
            if (count < 0) throw new IllegalArgumentException("Argument must be nonnegative: " + count);
            return run(5, count, null);
        }
        private long run(long operation, long argument, java.util.function.Consumer<? super Object> consumer) {
            Effects previous = EFFECTS.get(); Effects effects = new Effects(owner, owner.roots, null);
            effects.consumer = index -> consumer.accept(entries ? new Entry(owner, index) : owner.keyAt(index));
            EFFECTS.set(effects);
            try {
                long result = raw(operation, argument);
                if (effects.failure != null) return rethrow(effects.failure);
                if (result <= Long.MIN_VALUE + 20) throw new IllegalStateException("Native enchantment split failed: " + result);
                return result;
            } finally {
                if (previous == null) EFFECTS.remove(); else EFFECTS.set(previous);
                Reference.reachabilityFence(effects); Reference.reachabilityFence(this);
            }
        }
    }
    /** The iterator itself owns the actual native cursor allocation. */
    static final class KeyCursor extends Lease implements it.unimi.dsi.fastutil.objects.ObjectIterator<Object> {
        final Enchantments owner;
        final int kind;
        Entry recent;
        final Long2ObjectOpenHashMap<Object> wrapped = new Long2ObjectOpenHashMap<>() {
            @Override protected void rehash(int next) { if (next >= n) super.rehash(next); }
        };
        KeyCursor(Enchantments owner, int kind) {
            this.owner = owner;
            this.kind = kind;
            try { acquire(invoke(CURSOR_NEW, owner.handle())); }
            finally { Reference.reachabilityFence(owner); }
            if (kind == 2) recent = new Entry(owner, 0);
        }
        private long raw(long operation, long argument) {
            try { return (long)CURSOR_OP.invokeExact(handle(), operation, argument, Callback.STUB); }
            catch (Throwable failure) { return rethrow(failure); }
            finally { Reference.reachabilityFence(this); }
        }
        @Override public boolean hasNext() {
            long result = raw(0, 0);
            if (result != 0 && result != 1) throw new IllegalStateException("Native enchantment cursor failed: " + result);
            return result != 0;
        }
        private Object materialize(int index) {
            if (kind == 0) return owner.keyAt(index);
            if (kind == 1) recent = new Entry(owner, index); else recent.index = index;
            return recent;
        }
        @Override public Object next() {
            Entry prepared = kind == 1 ? new Entry(owner, 0) : null;
            int index = Math.toIntExact(run(1, 0, null));
            if (prepared != null) { prepared.index = index; recent = prepared; return prepared; }
            return materialize(index);
        }
        @Override public void remove() { run(2, 0, null); if (kind == 1) recent.index = -1; }
        @Override public int skip(int count) { return Math.toIntExact(run(4, count, null)); }
        @Override public void forEachRemaining(java.util.function.Consumer<? super Object> consumer) {
            run(3, 0, index -> consumer.accept(materialize(index)));
        }
        private long run(long operation, long argument, java.util.function.IntConsumer consumer) {
            Effects previous = EFFECTS.get();
            Effects effects = new Effects(owner, owner.roots, null);
            effects.cursor = this; effects.consumer = consumer;
            EFFECTS.set(effects);
            try {
                long result = raw(operation, argument);
                if (effects.failure != null) return rethrow(effects.failure);
                if (result == Long.MIN_VALUE + 10) throw new java.util.NoSuchElementException();
                if (result == Long.MIN_VALUE + 11) throw new IllegalStateException();
                if (result == Long.MIN_VALUE + 12) throw new NullPointerException("Cannot invoke \"it.unimi.dsi.fastutil.objects.ObjectArrayList.get(int)\" because \"this.wrapped\" is null");
                if (result == Long.MIN_VALUE + 13) throw new IndexOutOfBoundsException("Index (" + raw(6, 0) + ") is greater than or equal to list size (" + raw(7, 0) + ")");
                if (result == Long.MIN_VALUE + 14) throw new ArrayIndexOutOfBoundsException("Index " + raw(6, 0) + " out of bounds for length " + raw(7, 0));
                if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalStateException("Native enchantment cursor failed: " + result);
                return result;
            } finally {
                try { effects.finishWrapped(); }
                finally {
                    if (previous == null) EFFECTS.remove(); else EFFECTS.set(previous);
                    Reference.reachabilityFence(effects); Reference.reachabilityFence(this);
                }
            }
        }
    }
    private static final class Effects {
        final Enchantments owner;
        final TableRoots captured;
        final Object query;
        final Long2ObjectOpenHashMap<TableRoots> tables = new Long2ObjectOpenHashMap<>();
        Throwable failure;
        TableRoots binding;
        long boundToken;
        long hashToken;
        Object hashAnchor;
        KeyCursor cursor;
        java.util.function.IntConsumer consumer;
        long stagedWrapped;
        long selectedWrapped;
        Object selectedWrappedAnchor;
        Effects(Enchantments owner, TableRoots captured, Object query) {
            this.owner = owner; this.captured = captured; this.query = query;
            tables.put(captured.address, captured);
        }
        TableRoots table(long address) {
            TableRoots found = tables.get(address);
            if (found != null) return found;
            if (owner.roots.address == address) {
                tables.put(address, owner.roots);
                return owner.roots;
            }
            throw new IllegalStateException("Unanchored enchantment table");
        }
        Object resolve(long token, long table) {
            if (token == -1) return query;
            if (token == 0) return null;
            Object value = table(table).anchors.get(token);
            if (value == null && cursor != null) value = cursor.wrapped.get(token);
            if (value == null && token == selectedWrapped) value = selectedWrappedAnchor;
            if (value == null) throw new IllegalStateException("Missing enchantment holder anchor " + token);
            return value;
        }
        long bind(long address) {
            if (query == null) return 0;
            long token = owner.nextToken;
            if (token <= 0 || token == Long.MAX_VALUE) throw new IllegalStateException("Enchantment anchor space exhausted");
            owner.nextToken++;
            binding = table(address);
            boundToken = token;
            binding.anchors.put(token, query);
            return token;
        }
        void finish() {
            if (binding != null && invoke(TABLE_CONTAINS, binding.handle(), boundToken) == 0)
                binding.anchors.remove(boundToken);
        }
        void pinWrapped(long token, long table) {
            if (token == 0 || cursor.wrapped.containsKey(token)) return;
            stagedWrapped = token;
            cursor.wrapped.put(token, resolve(token, table));
        }
        void selectWrapped(long token, boolean retire) {
            selectedWrapped = token;
            selectedWrappedAnchor = cursor.wrapped.get(token);
            if (retire) cursor.wrapped.remove(token);
        }
        void finishWrapped() {
            if (stagedWrapped != 0 && cursor.raw(8, stagedWrapped) == 0) cursor.wrapped.remove(stagedWrapped);
        }
        void prepare(long address, long source) {
            table(source); // Pin the exact old array's VM anchors across later effects.
            TableRoots prepared = new TableRoots(address, true);
            tables.put(address, prepared);
        }
        void anchor(long token, long destination, long source) {
            if (token == 0 || table(destination).anchors.containsKey(token)) return;
            Object value = table(source).anchors.get(token);
            if (value == null && hashToken == token) value = hashAnchor;
            if (value == null) throw new IllegalStateException("Missing captured enchantment holder");
            table(destination).anchors.put(token, value);
        }
        long hash(long token, long table) {
            Object value = resolve(token, table);
            hashToken = token;
            hashAnchor = value;
            return value.hashCode();
        }
        void published(long address) {
            // Already prepared and rooted before native publication; assignment
            // itself neither allocates nor invokes user code.
            owner.roots = tables.get(address);
        }
    }
    private static final ThreadLocal<Effects> EFFECTS = new ThreadLocal<>();
    private static long effect(long operation, long first, long second, long table) {
        Effects effects = EFFECTS.get();
        if (effects == null) return Long.MIN_VALUE;
        try {
            return switch ((int)operation) {
                case 0 -> effects.hash(first, table);
                case 1 -> effects.resolve(first, table).equals(effects.resolve(second, table)) ? 1 : 0;
                case 2 -> effects.bind(table);
                case 3 -> { effects.prepare(first, table); yield 0; }
                case 4 -> { effects.anchor(first, second, table); yield 0; }
                case 5 -> { effects.published(first); yield 0; }
                case 6 -> { effects.table(table).anchors.remove(first); yield 0; }
                case 7 -> { effects.pinWrapped(first, table); yield 0; }
                case 8 -> { effects.selectWrapped(first, second != 0); yield 0; }
                case 9 -> { effects.consumer.accept(Math.toIntExact(first)); yield 0; }
                case 10 -> { effects.table(first); yield 0; }
                case 12 -> { throw new ArrayIndexOutOfBoundsException("Index " + first + " out of bounds for length " + second); }
                default -> throw new IllegalArgumentException("Unknown enchantment effect " + operation);
            };
        } catch (Throwable failure) { effects.failure = failure; return Long.MIN_VALUE; }
    }
    private static final class Callback {
        static final MemorySegment STUB;
        static {
            try {
                var method = MethodHandles.lookup().findStatic(NativeItemRoots.class, "effect",
                    MethodType.methodType(long.class, long.class, long.class, long.class, long.class));
                STUB = Linker.nativeLinker().upcallStub(method, FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Arena.global());
            } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
    }
}
