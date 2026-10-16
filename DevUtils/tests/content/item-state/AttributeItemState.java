package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.util.NativeLibraryLoader;

/** Attribution controls on one sealed image. Exclusions are deliberate, not acceptance shortcuts. */
public final class AttributeItemState {
    private static final MethodHandle ID;
    private static final MethodHandle LIVE = call("owner_live"), VERSION = call("access_version"), PREPARE = call("access_prepare"),
        COPY = call("access_copy"), RELEASE = call("owner_release");
    private static final FunctionDescriptor SWAP_TYPE = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
    private static final MethodHandle NORMAL_SWAP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_atomic_swap", SWAP_TYPE);
    private static final MethodHandle CRITICAL_SWAP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_atomic_swap", SWAP_TYPE, Linker.Option.critical(false));
    private static final NativeItemState.AtomicCell cell = new NativeItemState.AtomicCell();
    private static final MemorySegment cellView;
    private static final List<DataComponentType<Object>> keys = new ArrayList<>();
    private static final long[] ids = new long[257];
    private static final NativeItemState.AccessOwner[][] owners = new NativeItemState.AccessOwner[2][64];
    private static final NativeItemState.AccessRoots[][] roots = new NativeItemState.AccessRoots[2][64];
    private static final long[][] patchHandles = new long[2][64];
    private static final List<String> rows = new ArrayList<>();
    private static volatile long sink;
    static {
        try {
            var method = Class.forName("net.minecraft.world.item.NativeItemState$Identities").getDeclaredMethod("of", Object.class);
            method.setAccessible(true); ID = MethodHandles.lookup().unreflect(method);
            var field = NativeItemState.AtomicCell.class.getDeclaredField("cell"); field.setAccessible(true); cellView = (MemorySegment)field.get(cell);
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static MethodHandle call(String suffix) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_" + suffix, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    }
    private static long invoke(MethodHandle method, long value) {
        try { long result = (long)method.invokeExact(value); if (result < 0) throw new AssertionError(result); return result; }
        catch (Throwable failure) { throw new AssertionError(failure); }
    }
    private static long identity(Object value) {
        try { return (long)ID.invokeExact(value); } catch (Throwable failure) { throw new AssertionError(failure); }
    }
    private static long identities(int count) {
        long sum = 0; for (int i = 0; i < 500_000; i++) sum += identity(keys.get((i >>> 6) % count)); return sum;
    }
    private static long cachedRead(int shape) {
        int count = shape == 0 ? 1 : 32; long sum = 0;
        for (int i = 0; i < 500_000; i++) sum += ((long[])roots[shape][i & 63].read(ids[(i >>> 6) % count]))[0];
        java.lang.ref.Reference.reachabilityFence(owners); return sum;
    }
    private static long ownerRead(MethodHandle method, boolean patch) {
        long sum = 0;
        for (int i = 0; i < 500_000; i++) sum += invoke(method, patch ? patchHandles[0][i & 63] : owners[0][i & 63].handle);
        java.lang.ref.Reference.reachabilityFence(owners); return sum;
    }
    private static long crossing(boolean critical) {
        long sum = 0;
        try {
            for (int i = 0; i < 500_000; i++) sum += critical ? (long)CRITICAL_SWAP.invokeExact(cellView, (long)(i & 1)) : (long)NORMAL_SWAP.invokeExact(cellView, (long)(i & 1));
            return sum;
        } catch (Throwable failure) { throw new AssertionError(failure); }
        finally { java.lang.ref.Reference.reachabilityFence(cellView); }
    }
    private static long nativeCopy(int shape) {
        long sum = 0;
        for (int i = 0; i < 20_000; i++) {
            long copied = invoke(COPY, owners[shape][i & 63].handle);
            try { sum += invoke(PREPARE, copied) > 0 ? 1 : 0; }
            finally { if (invoke(RELEASE, copied) != 1) throw new AssertionError("copy lease not released"); }
        }
        java.lang.ref.Reference.reachabilityFence(owners); return sum;
    }
    private static long compiler() { return ManagementFactory.getCompilationMXBean().getTotalCompilationTime(); }
    private static long gc() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum(); }
    private static long allocated() { return ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    private static void measure(String name, int operations, LongSupplier work) throws Exception {
        long begin = System.nanoTime(), quiet = begin, previous = compiler(); int rounds = 0;
        while (true) {
            sink ^= work.getAsLong(); rounds++; Thread.sleep(25);
            long now = System.nanoTime(), current = compiler(); if (current != previous) quiet = now; previous = current;
            if (rounds >= 5 && now - begin >= 1_000_000_000L && now - quiet >= 250_000_000L || now - begin >= 12_000_000_000L) break;
        }
        rows.add("# warmup " + name + " rounds=" + rounds);
        for (int sample = 0; sample < 5; sample++) {
            long beforeCompiler = compiler(), beforeGc = gc(), beforeAllocated = allocated(), start = System.nanoTime();
            long checksum = work.getAsLong(), elapsed = System.nanoTime() - start; sink ^= checksum;
            rows.add(name + "," + sample + "," + operations + "," + elapsed + "," + checksum + "," + (compiler() - beforeCompiler) + "," + (gc() - beforeGc) + "," + (allocated() - beforeAllocated));
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for (int i = 0; i < ids.length; i++) {
            var key = DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(v -> v, Object::toString)).build(); keys.add(key); ids[i] = identity(key);
        }
        var rootsField = NativeItemState.AccessOwner.class.getDeclaredField("roots"); rootsField.setAccessible(true);
        var handleField = NativeItemState.AccessRoots.class.getDeclaredField("nativeHandle"); handleField.setAccessible(true);
        for (int shape = 0; shape < 2; shape++) for (int i = 0; i < 64; i++) {
            var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); owners[shape][i] = owner;
            for (int j = 0; j < (shape == 0 ? 1 : 32); j++) owner.set(keys.get(j), new long[]{i + j});
            roots[shape][i] = (NativeItemState.AccessRoots)rootsField.get(owner); patchHandles[shape][i] = handleField.getLong(roots[shape][i]);
        }
        rows.add("# ATTRIBUTION ONLY: current sealed image; setup-cached IDs exclude public identity lookup; native clone excludes Java root adoption; crossing includes identical atomic swap; components are not additive timings");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        measure("weak_identity_1_key", 500_000, () -> identities(1));
        measure("weak_identity_32_keys", 500_000, () -> identities(32));
        measure("weak_identity_257_keys", 500_000, () -> identities(257));
        measure("native_read_cached_id_1_entry", 500_000, () -> cachedRead(0));
        measure("native_read_cached_id_32_entries", 500_000, () -> cachedRead(1));
        measure("normal_cross_same_atomic_swap", 500_000, () -> crossing(false));
        measure("critical_cross_same_atomic_swap", 500_000, () -> crossing(true));
        measure("pool_contains_owner", 500_000, () -> ownerRead(LIVE, false));
        measure("pool_arc_version", 500_000, () -> ownerRead(VERSION, true));
        measure("pool_arc_owner_lock_no_cow", 500_000, () -> ownerRead(PREPARE, false));
        measure("native_copy_prepare_release_1_entry", 20_000, () -> nativeCopy(0));
        measure("native_copy_prepare_release_32_entries", 20_000, () -> nativeCopy(1));
        Files.write(Path.of(args[0]), rows); System.out.println("ATTRIBUTION sink=" + sink);
    }
}
