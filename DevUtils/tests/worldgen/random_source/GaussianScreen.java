package net.minecraft.world.level.levelgen;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.util.RandomSource;

/** Actual Gaussian producer/cache/source creation and bounded owner retirement. */
public final class GaussianScreen {
    static volatile Object retained;
    static volatile long sink;
    record Batch(RandomSource[] sources, MarsagliaPolarGaussian[] gaussians) {}
    record Owners(long[] roots, long[] sources, long[] gaussian) {}
    static long[] stats(String name) throws Exception {
        try {
            var type = Class.forName("net.minecraft.world.level.levelgen." + name);
            var method = type.getDeclaredMethod("statistics"); method.setAccessible(true);
            return (long[]) method.invoke(null);
        } catch (ClassNotFoundException absent) { return new long[0]; }
    }
    static Owners owners() throws Exception {
        return new Owners(stats("NativeRandomBits"), stats("NativeXoroshiroSource"), stats("NativeGaussian"));
    }
    static boolean retired(Owners value, Owners initial) {
        return (value.roots.length == 0 || value.roots[0] <= initial.roots[0])
            && (value.sources.length == 0 || value.sources[0] <= initial.sources[0])
            && (value.gaussian.length == 0 || value.gaussian[0] <= initial.gaussian[0]
                && value.gaussian[3] <= initial.gaussian[3]);
    }
    static void settle() throws Exception {
        retained = null;
        for (int i = 0; i < 3; i++) { System.gc(); Thread.sleep(10); }
    }
    static void drain(Owners initial) throws Exception {
        for (int i = 0; i < 100; i++) {
            settle();
            if (retired(owners(), initial)) return;
        }
        throw new AssertionError("actual source/cache/frame owners did not retire");
    }
    static Batch prepare(int kind, int count) {
        var data = new Batch(new RandomSource[count], new MarsagliaPolarGaussian[count]);
        if (kind != 4) for (int i = 0; i < count; i++) {
            data.sources[i] = kind == 1 ? new LegacyRandomSource(1234567L + i) : new XoroshiroRandomSource(1234567L + i);
            if (kind == 2) data.gaussians[i] = new MarsagliaPolarGaussian(data.sources[i]);
            if (kind == 3) data.sources[i].nextGaussian();
        }
        return data;
    }
    static long work(int kind, Batch data) {
        long sum = 0;
        for (int i = 0; i < data.sources.length; i++) {
            if (kind == 4) data.sources[i] = new XoroshiroRandomSource(1234567L + i);
            var source = data.sources[i];
            int draws = kind == 3 ? 1 : kind == 4 ? 3 : 32;
            for (int j = 0; j < draws; j++) {
                double value = kind == 2 ? data.gaussians[i].nextGaussian() : source.nextGaussian();
                sum = Long.rotateLeft(sum, 7) ^ Double.doubleToRawLongBits(value);
            }
            sum ^= source.nextLong();
        }
        retained = data; sink = sum; return sum;
    }
    static long gc() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }
    static long created(long[] before, long[] after, int index) {
        return before.length == 0 ? -1 : after[index] - before[index];
    }
    static void origin(Class<?> type) throws Exception {
        var url = type.getResource(type.getSimpleName() + ".class");
        try (var input = url.openStream()) {
            System.out.println("SOURCE_ORIGIN " + type.getName() + " " + url + " "
                + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        }
    }
    public static void main(String[] args) throws Exception {
        boolean smoke = Arrays.asList(args).contains("--smoke");
        int only = -1;
        for (String arg : args) if (arg.startsWith("--kind=")) only = Integer.parseInt(arg.substring(7));
        int target = smoke ? 1 : 6, warmup = smoke ? 2 : 24;
        origin(XoroshiroRandomSource.class); origin(LegacyRandomSource.class); origin(MarsagliaPolarGaussian.class);
        var thread = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        var compiler = ManagementFactory.getCompilationMXBean();
        if (!thread.isThreadAllocatedMemorySupported()) throw new IllegalStateException("thread allocation measurement unsupported");
        thread.setThreadAllocatedMemoryEnabled(true);
        if (!thread.isThreadCpuTimeEnabled()) thread.setThreadCpuTimeEnabled(true);
        long tid = Thread.currentThread().threadId();
        for (int kind = 0; kind < 5; kind++) {
            if (only >= 0 && kind != only) continue;
            settle(); var initial = owners(); int accepted = 0;
            int count = smoke ? 16 : kind == 3 ? 262144 : 4096;
            for (int batch = 0; batch < 90 && accepted < target; batch++) {
                drain(initial); var data = prepare(kind, count); var before = owners();
                long jit = compiler.getTotalCompilationTime(), collections = gc();
                long allocated = thread.getThreadAllocatedBytes(tid), cpu = thread.getCurrentThreadCpuTime(), start = System.nanoTime();
                long sum = work(kind, data);
                long elapsed = System.nanoTime() - start;
                cpu = thread.getCurrentThreadCpuTime() - cpu;
                allocated = thread.getThreadAllocatedBytes(tid) - allocated;
                long jitDelta = compiler.getTotalCompilationTime() - jit, gcDelta = gc() - collections;
                var after = owners();
                long roots = created(before.roots, after.roots, 1), sources = created(before.sources, after.sources, 1);
                long caches = created(before.gaussian, after.gaussian, 1), frames = created(before.gaussian, after.gaussian, 4);
                if (roots >= 0 && roots != 0) throw new AssertionError("unexpected generator projection");
                if (sources >= 0 && sources != (kind == 4 ? count : 0)) throw new AssertionError("source creation work");
                if (caches >= 0 && caches != (kind == 1 || kind == 2 || kind == 4 ? count : 0)) throw new AssertionError("cache creation work");
                if (frames >= 0 && frames != (kind == 3 ? 0 : kind == 4 ? 2L * count : 16L * count)) throw new AssertionError("frame creation work");
                if (after.gaussian.length != 0 && after.gaussian[3] != before.gaussian[3]) throw new AssertionError("live draw frame after call");
                boolean quiet = batch >= warmup && jitDelta == 0 && gcDelta == 0;
                if (quiet) accepted++;
                System.out.println("PRODUCER_SCREEN {\"kind\":" + kind + ",\"batch\":" + batch + ",\"accepted\":" + quiet
                    + ",\"count\":" + count + ",\"elapsed_ns\":" + elapsed + ",\"cpu_ns\":" + cpu + ",\"java_bytes\":" + allocated
                    + ",\"jit_ms\":" + jitDelta + ",\"gc\":" + gcDelta + ",\"sum\":" + sum + ",\"root_created\":" + roots
                    + ",\"source_created\":" + sources + ",\"cache_created\":" + caches + ",\"frame_created\":" + frames + "}");
            }
            if (accepted != target) throw new AssertionError("insufficient quiet samples");
            drain(initial); var end = owners();
            System.out.println("PRODUCER_RETIREMENT " + kind + " roots=" + Arrays.toString(end.roots)
                + " sources=" + Arrays.toString(end.sources) + " cache/frame=" + Arrays.toString(end.gaussian));
        }
        Thread.sleep(250);
    }
}
