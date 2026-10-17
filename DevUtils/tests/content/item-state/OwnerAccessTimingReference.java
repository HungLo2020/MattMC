package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;

/** Matched dynamic-key owner composition diagnostic, compiled unchanged on Frozen. */
public final class OwnerAccessTimingReference {
    public interface Store {
        Object get(DataComponentType<Object> key);
        Object set(DataComponentType<Object> key, Object value);
        Object remove(DataComponentType<Object> key);
        Store copy();
        List<DataComponentType<?>> order();
        Patch patch();
    }
    public interface Patch {
        java.util.Optional<?> optional(DataComponentType<Object> key);
        void remove(DataComponentType<Object> key);
    }
    private static final class JavaStore implements Store {
        final PatchedDataComponentMap map;
        JavaStore(DataComponentMap prototype) { this.map = new PatchedDataComponentMap(prototype); }
        JavaStore(PatchedDataComponentMap map) { this.map = map; }
        public Object get(DataComponentType<Object> key) { return this.map.get(key); }
        public Object set(DataComponentType<Object> key, Object value) { return this.map.set(key, value); }
        public Object remove(DataComponentType<Object> key) { return this.map.remove(key); }
        public Store copy() { return new JavaStore(this.map.copy()); }
        public Patch patch() {
            var patch = this.map.asPatch();
            if (patch.isEmpty()) return null;
            return new Patch() {
                public java.util.Optional<?> optional(DataComponentType<Object> key) { return patch.get(key); }
                public void remove(DataComponentType<Object> key) {
                    var iterator = patch.entrySet().iterator();
                    while (iterator.hasNext()) if (iterator.next().getKey() == key) { iterator.remove(); return; }
                }
            };
        }
        public List<DataComponentType<?>> order() { return new ArrayList<>(this.map.asPatch().entrySet().stream().map(java.util.Map.Entry::getKey).toList()); }
    }
    private static long prototypeCalls, equalsCalls;
    private static final class Value {
        final long number;
        Runnable beforeEquals = () -> {};
        Value(long number) { this.number = number; }
        public boolean equals(Object other) { equalsCalls++; this.beforeEquals.run(); return other instanceof Value value && value.number == this.number; }
        public int hashCode() { return Long.hashCode(this.number); }
    }
    private static final List<DataComponentType<Object>> keys = new ArrayList<>();
    private static final Store[] stores = new Store[64];
    private static final Value[] values = new Value[64];
    private static final List<String> rows = new ArrayList<>();
    private static final int OPERATIONS = 100_000;
    private static int patchKeys = 32;
    private static volatile long sink;
    private static long number(Object value) { return value == null ? -1 : ((Value)value).number; }
    private static long allocated() {
        return ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId());
    }
    private static long compiler() { return ManagementFactory.getCompilationMXBean().getTotalCompilationTime(); }
    private static long gc() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum(); }
    private static long read(boolean prototype, boolean dynamic) {
        long sum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            int key = dynamic ? (i >>> 6) % keys.size() : prototype ? (i >>> 6) & 15 : 16 + ((i >>> 6) & (patchKeys - 1));
            sum += number(stores[i & 63].get(keys.get(key)));
        }
        return sum;
    }
    private static long replace() {
        long sum = 0;
        for (int i = 0; i < OPERATIONS; i++) sum += number(stores[i & 63].set(keys.get(16 + ((i >>> 6) & (patchKeys - 1))), values[(i >>> 5) & 63]));
        return sum;
    }
    private static long removeRestore() {
        long sum = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            Store store = stores[i & 63]; var key = keys.get(16 + ((i >>> 6) & (patchKeys - 1)));
            sum += number(store.remove(key)); sum += number(store.set(key, values[(i >>> 5) & 63]));
        }
        return sum;
    }
    private static long copyWrite() {
        long sum = 0;
        for (int i = 0; i < 1000; i++) {
            Store original = stores[i & 63], copy = original.copy(); var key = keys.get(16 + ((i >>> 6) & (patchKeys - 1)));
            sum += number(copy.set(key, values[(i >>> 5) & 63])); sum += number(copy.get(key)); sum += number(original.get(key));
        }
        return sum;
    }
    private static void contracts(DataComponentMap prototype, java.lang.reflect.Method factory) throws Exception {
        Store store = factory == null ? new JavaStore(prototype) : (Store)factory.invoke(null, prototype);
        var key = keys.get(0); Value first = new Value(100), second = new Value(101);
        rows.add("# contract tombstone=" + number(store.remove(key)) + "/" + number(store.remove(key)) + "/" + number(store.set(key, first)));
        store.set(keys.get(16), values[7]);
        var escaped = new java.util.concurrent.atomic.AtomicReference<Patch>();
        var copied = new java.util.concurrent.atomic.AtomicReference<Store>();
        second.beforeEquals = () -> { escaped.set(store.patch()); copied.set(store.copy()); };
        rows.add("# contract outer_previous=" + number(store.set(key, second))); second.beforeEquals = () -> {};
        rows.add("# contract callback_alias=" + (escaped.get().optional(key).orElseThrow() == second) + "/" + (copied.get().get(key) == second));
        rows.add("# contract optional_identity=" + (escaped.get().optional(key) == copied.get().patch().optional(key)));
        store.set(keys.get(16), values[8]); escaped.get().remove(key);
        rows.add("# contract detach_and_escaped_remove=" + number(store.get(key)) + "/" + number(copied.get().get(key)));
        Value third = new Value(102); var beforeNested = new java.util.concurrent.atomic.AtomicReference<Patch>();
        third.beforeEquals = () -> { beforeNested.set(store.patch()); store.set(keys.get(17), values[9]); };
        store.set(key, third); third.beforeEquals = () -> {};
        rows.add("# contract nested_cow=" + number(beforeNested.get().optional(key).orElseThrow()) + "/" + number(store.get(key)));
        Value failing = new Value(103); var original = new AssertionError("original owner equality"); failing.beforeEquals = () -> { throw original; };
        boolean exact = false;
        try { store.set(key, failing); } catch (AssertionError thrown) { exact = thrown == original; }
        rows.add("# contract original_throw=" + exact + "/" + number(store.get(key)));
        var ids = new ArrayList<Integer>(); for (var type : store.order()) ids.add(keys.indexOf(type));
        rows.add("# contract native_order=" + ids);
        DataComponentType<Object> nullKey = null; Value nullValue = new Value(200);
        boolean nullInsert = store.set(nullKey, nullValue) == null;
        Patch nullPatch = store.patch(); Store nullCopy = store.copy(); store.remove(nullKey);
        boolean nullDetached = store.get(nullKey) == null && nullCopy.get(nullKey) == nullValue;
        boolean nullOptional = nullPatch.optional(nullKey).orElseThrow() == nullValue;
        nullPatch.remove(nullKey);
        rows.add("# contract null_type=" + nullInsert + "/" + nullDetached + "/" + nullOptional + "/" + (nullCopy.get(nullKey) == null));
    }
    private static void reset() {
        for (int i = 0; i < stores.length; i++) for (int j = 16; j < 16 + patchKeys; j++) stores[i].set(keys.get(j), values[(i + j) & 63]);
    }
    private static void measure(String name, int operations, LongSupplier work) throws Exception {
        long begin = System.nanoTime(), quiet = begin, previous = compiler(); int rounds = 0;
        while (true) {
            sink ^= work.getAsLong(); rounds++; Thread.sleep(25);
            long now = System.nanoTime(), current = compiler(); if (current != previous) quiet = now; previous = current;
            if (rounds >= 5 && now - begin >= 1_000_000_000L && now - quiet >= 250_000_000L || now - begin >= 12_000_000_000L) break;
        }
        reset(); rows.add("# warmup " + name + " rounds=" + rounds);
        for (int sample = 0; sample < 5; sample++) {
            prototypeCalls = 0; equalsCalls = 0;
            long beforeCompiler = compiler(), beforeGc = gc(), beforeAllocated = allocated(), started = System.nanoTime();
            long checksum = work.getAsLong(), elapsed = System.nanoTime() - started; sink ^= checksum;
            rows.add(name + "," + sample + "," + operations + "," + elapsed + "," + checksum + "," + (compiler() - beforeCompiler) + "," + (gc() - beforeGc) + "," + prototypeCalls + "," + equalsCalls + "," + (allocated() - beforeAllocated));
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for (int i = 0; i < 257; i++) keys.add(DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(v -> (Object)v, Object::toString)).build());
        for (int i = 0; i < values.length; i++) values[i] = new Value(i);
        var builder = DataComponentMap.builder(); for (int i = 0; i < 16; i++) builder.set(keys.get(i), values[i]);
        DataComponentMap base = builder.build();
        DataComponentMap prototype = new DataComponentMap() {
            public <T> T get(DataComponentType<? extends T> key) { prototypeCalls++; return base.get(key); }
            public Set<DataComponentType<?>> keySet() { return base.keySet(); }
        };
        patchKeys = args.length > 2 && args[2].equals("small") ? 1 : 32;
        boolean nativeOwner = args[1].equals("dynamic_owner");
        var factory = nativeOwner ? Class.forName("net.minecraft.world.item.OwnerAccessTimingCurrent").getMethod("create", DataComponentMap.class) : null;
        for (int i = 0; i < stores.length; i++) stores[i] = nativeOwner ? (Store)factory.invoke(null, prototype) : new JavaStore(prototype);
        reset();
        List<DataComponentType<?>> expected = new ArrayList<>(keys.subList(16, 16 + patchKeys));
        for (Store store : stores) {
            if (!store.order().equals(expected)) throw new AssertionError("native order mismatch");
            Object value = store.remove(keys.get(16)); store.set(keys.get(16), value);
            var shifted = new ArrayList<>(expected); shifted.remove(keys.get(16)); shifted.add(keys.get(16));
            if (!store.order().equals(shifted)) throw new AssertionError("remove/reinsert order mismatch");
        }
        rows.add("# mode=" + args[1] + " native_dynamic_owner=" + nativeOwner + " active_overrides=" + patchKeys + " foreign_keys=257 prototype_callbacks=true exclusions=native_default_store,ItemStack,container_output");
        contracts(prototype, factory);
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,prototype_calls,equals_calls,allocated_bytes");
        measure("get_override_dynamic_keys", OPERATIONS, () -> read(false, false));
        measure("get_prototype_callback", OPERATIONS, () -> read(true, false));
        measure("get_257_foreign_keys", OPERATIONS, () -> read(false, true));
        measure("set_with_prototype_and_equals", OPERATIONS, OwnerAccessTimingReference::replace);
        measure("remove_restore_pair", OPERATIONS, OwnerAccessTimingReference::removeRestore);
        measure("copy_plus_write", 1000, OwnerAccessTimingReference::copyWrite);
        Files.write(Path.of(args[0]), rows); System.out.println("DYNAMIC_OWNER_COMPOSITION sink=" + sink);
    }
}
