package net.minecraft.world.level.lighting;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** Times actual empty-map snapshot copies; validates real owner retirement untimed. */
public final class BenchmarkLightMapRetirement {
    static volatile long sink;
    static Method registered, released, pending, live;
    static Field nativeMap;
    static final com.sun.management.ThreadMXBean MEMORY = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static long value(Method method, Object... arguments) throws Exception { return method == null ? 0 : ((Number)method.invoke(null, arguments)).longValue(); }
    final LevelLightEngine engine;
    final DataLayerStorageMap<?> source;
    final DataLayerStorageMap<?>[] copies;
    final int count;
    BenchmarkLightMapRetirement(int count) {
        this.count = count; this.copies = new DataLayerStorageMap<?>[count];
        var chunks = new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x, int z) { return null; }
            public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
        };
        engine = new LevelLightEngine(chunks, true, false);
        source = ((BlockLightEngine)engine.getLayerListener(LightLayer.BLOCK)).storage.updatingSectionData;
    }
    // Kept out of the caller by the matched JVM CompileCommand. This lets
    // diagnostic weak references observe actual map roots without a JIT local retaining them.
    static WeakReference<?>[] releaseCopies(DataLayerStorageMap<?>[] copies) throws Exception {
        var weak = new WeakReference<?>[copies.length];
        for (int i = 0; i < copies.length; i++) {
            if (copies[i].getLayer(0L) != null) throw new AssertionError("Empty snapshot changed");
            Object owner = nativeMap == null ? copies[i] : nativeMap.get(copies[i]);
            weak[i] = new WeakReference<>(owner); copies[i] = null;
        }
        return weak;
    }
    static boolean allCollected(WeakReference<?>[] weak) { return Arrays.stream(weak).allMatch(w -> w.refersTo(null)); }
    void sample(String prefix, int index) throws Exception {
        long registrations = value(registered, (byte)16), releases = value(released), beforePending = value(pending);
        long thread = Thread.currentThread().threadId();
        long jit = ManagementFactory.getCompilationMXBean().getTotalCompilationTime(), gc = gcCount();
        long bytes = MEMORY.getThreadAllocatedBytes(thread), start = System.nanoTime();
        for (int i = 0; i < count; i++) copies[i] = source.copy();
        long ns = System.nanoTime() - start, allocated = MEMORY.getThreadAllocatedBytes(thread) - bytes;
        long compilation = ManagementFactory.getCompilationMXBean().getTotalCompilationTime() - jit, collections = gcCount() - gc;
        long added = value(registered, (byte)16) - registrations;
        if (registered != null && added != count) throw new AssertionError("Native root registration work changed " + added);
        var weak = releaseCopies(copies); long deadline = System.nanoTime() + 5_000_000_000L;
        while ((!allCollected(weak) || released != null && (value(released) - releases < count || value(pending) > beforePending)) && System.nanoTime() < deadline) {
            System.gc(); Thread.sleep(25);
        }
        if (!allCollected(weak) || released != null && (value(released) - releases < count || value(pending) > beforePending)) throw new AssertionError("Actual map root retirement did not settle");
        sink = count; String status = Files.readString(Path.of("/proc/self/status"));
        System.out.println(prefix + " " + index + " ns=" + ns + " bytes=" + allocated + " copies=" + count + " null_reads=" + count
            + " jit=" + compilation + " gc=" + collections + " registered=" + added + " released=" + (value(released) - releases)
            + " pending_delta=" + (value(pending) - beforePending) + " weak_remaining=0");
        System.out.println("RESOURCE " + prefix + " " + index + " " + status.lines().filter(s -> s.startsWith("VmRSS:") || s.startsWith("VmSwap:")).collect(java.util.stream.Collectors.joining(" ")));
    }
    static long gcCount() { long n = 0; for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) n += Math.max(0, gc.getCollectionCount()); return n; }
    static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception { var m = type.getDeclaredMethod(name, parameters); m.setAccessible(true); return m; }
    static void run(int count) throws Exception {
        var benchmark = new BenchmarkLightMapRetirement(count); int warm = 0, settled = 0;
        var compilation = ManagementFactory.getCompilationMXBean();
        while (warm < 64 || settled < 12) {
            if (warm == 512) throw new AssertionError("Compiler did not settle");
            long before = compilation.getTotalCompilationTime(); benchmark.sample("WARM", warm++);
            settled = before == compilation.getTotalCompilationTime() ? settled + 1 : 0;
        }
        for (int i = 0; i < 12; i++) benchmark.sample("SAMPLE", i);
        java.lang.ref.Reference.reachabilityFence(benchmark.engine);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); MEMORY.setThreadAllocatedMemoryEnabled(true);
        try {
            Class<?> reclaimer = Class.forName("net.minecraft.world.level.chunk.NativeReclaimer");
            registered = method(reclaimer, "registered", byte.class); released = method(reclaimer, "released"); pending = method(reclaimer, "pending");
            live = method(Class.forName("net.minecraft.world.level.lighting.NativeLightCoordinator"), "liveForVerification");
            nativeMap = DataLayerStorageMap.class.getDeclaredField("nativeMap"); nativeMap.setAccessible(true);
        } catch (ClassNotFoundException frozen) { registered = released = pending = live = null; nativeMap = null; }
        long owners = value(live); run(Integer.parseInt(args[0])); long deadline = System.nanoTime() + 5_000_000_000L;
        while (value(live) != owners && System.nanoTime() < deadline) { System.gc(); Thread.sleep(25); }
        if (value(live) != owners) throw new AssertionError("Actual final native owner did not retire");
        System.out.println("RETIREMENT_FINAL native_owner_delta=0");
    }
}
