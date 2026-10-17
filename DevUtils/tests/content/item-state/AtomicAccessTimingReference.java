package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;

/** Isolated one-known-cell lower bound, NOT a full component-owner acceptance workload. */
public final class AtomicAccessTimingReference {
    public interface Store { Object get(); Object set(Object value); }
    private static final DataComponentType<Object> KEY = DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(s -> (Object)s, Object::toString)).build();
    private static final class JavaStore implements Store {
        final PatchedDataComponentMap map = new PatchedDataComponentMap(DataComponentMap.EMPTY);
        public Object get() { return map.get(KEY); }
        public Object set(Object value) { return map.set(KEY, value); }
    }
    static final int OPERATIONS = 500_000;
    static final Store[] stores = new Store[64];
    static final Object[] values = new Object[64];
    static final List<String> rows = new ArrayList<>();
    static volatile long sink;
    static long compiler() { return ManagementFactory.getCompilationMXBean().getTotalCompilationTime(); }
    static long gc() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum(); }
    static long read() { long sum = 0; for (int i = 0; i < OPERATIONS; i++) sum += ((long[])stores[i & 63].get())[0]; return sum; }
    static long replace() {
        long sum = 0;
        for (int i = 0; i < OPERATIONS; i++) { Object old = stores[i & 63].set(values[(i >>> 6) & 63]); sum += old == null ? -1 : ((long[])old)[0]; }
        return sum;
    }
    static void measure(String name, LongSupplier work) throws Exception {
        long begin = System.nanoTime(), quiet = begin, previous = compiler(); int rounds = 0;
        while (true) {
            sink ^= work.getAsLong(); rounds++; Thread.sleep(25);
            long now = System.nanoTime(), current = compiler(); if (current != previous) quiet = now; previous = current;
            if (rounds >= 5 && now - begin >= 1_000_000_000L && now - quiet >= 250_000_000L || now - begin >= 12_000_000_000L) break;
        }
        for (int i = 0; i < stores.length; i++) stores[i].set(values[i]);
        rows.add("# warmup " + name + " rounds=" + rounds);
        for (int sample = 0; sample < 5; sample++) {
            long beforeCompiler = compiler(), beforeGc = gc(), started = System.nanoTime();
            long checksum = work.getAsLong(), elapsed = System.nanoTime() - started; sink ^= checksum;
            rows.add(name + "," + sample + "," + OPERATIONS + "," + elapsed + "," + checksum + "," + (compiler() - beforeCompiler) + "," + (gc() - beforeGc));
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        boolean kernel = args[1].equals("atomic_lower_bound");
        java.lang.reflect.Method factory = kernel ? Class.forName("net.minecraft.world.item.AtomicAccessTimingCurrent").getMethod("create") : null;
        for (int i = 0; i < stores.length; i++) { values[i] = new long[]{i}; stores[i] = kernel ? (Store)factory.invoke(null) : new JavaStore(); stores[i].set(values[i]); }
        rows.add("# mode=" + args[1] + " native_lower_bound=" + kernel + " exclusions=key_lookup,prototype,COW,ordered_insert_remove,callbacks,ItemStack,container_output");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count");
        measure("get_opaque", AtomicAccessTimingReference::read); measure("replace_opaque_lower_bound", AtomicAccessTimingReference::replace);
        Files.write(Path.of(args[0]), rows); System.out.println("ATOMIC_ACCESS_LOWER_BOUND sink=" + sink);
    }
}
