package net.minecraft.server.level;

import java.lang.management.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Diagnostic only. Timed producers are the unchanged complete-workload cycle.
 * GC/reclaimer snapshots bracket that window; observation/formatting stays out.
 * Counter deltas establish correlation, not native CPU percentages. */
public final class ProfileLightLifecycle {
    private static final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private static Method pending, released, registered;
    private static byte[] kinds;
    private record Snapshot(long gcCount, long gcMs, long pending, long released, long[] registered) {}

    private static void bind() throws Exception {
        if (Boolean.getBoolean("threadedLight.frozen")) {
            kinds = new byte[0];
            return;
        }
        try {
            Class<?> type = Class.forName("net.minecraft.world.level.chunk.NativeReclaimer");
            pending = type.getDeclaredMethod("pending"); pending.setAccessible(true);
            released = type.getDeclaredMethod("released"); released.setAccessible(true);
            registered = type.getDeclaredMethod("registered", byte.class); registered.setAccessible(true);
            String[] names = {"LIGHT_LAYER", "LIGHT_VIEW", "LIGHT_MAP", "LIGHT_MAP_VIEW"};
            kinds = new byte[names.length];
            for (int i = 0; i < names.length; i++) {
                Field field = type.getDeclaredField(names[i]); field.setAccessible(true); kinds[i] = field.getByte(null);
            }
        } catch (ClassNotFoundException frozen) {
            kinds = new byte[0];
        }
    }

    private static Snapshot snapshot() throws Exception {
        long count = 0, ms = 0;
        for (var collector : collectors) {
            count += Math.max(0, collector.getCollectionCount());
            ms += Math.max(0, collector.getCollectionTime());
        }
        long[] registrations = new long[kinds.length];
        for (int i = 0; i < kinds.length; i++) registrations[i] = ((Number)registered.invoke(null, kinds[i])).longValue();
        return new Snapshot(count, ms, pending == null ? 0 : ((Number)pending.invoke(null)).longValue(),
            released == null ? 0 : ((Number)released.invoke(null)).longValue(), registrations);
    }

    private static void sample(VerifyThreadedLighting.Fixture fixture, int count, int steps,
            int index, String prefix, CompilationMXBean compilation,
            com.sun.management.ThreadMXBean memory, long thread) throws Exception {
        BenchmarkThreadedLighting.checksum = 0;
        Snapshot before = snapshot();
        long compiled = compilation.getTotalCompilationTime(), allocated = memory.getThreadAllocatedBytes(thread);
        long dispatch = fixture.dispatch.executed, light = fixture.light.executed;
        long began = System.nanoTime();
        for (int i = 0; i < steps; i++) BenchmarkThreadedLighting.cycle(fixture, count, i);
        long elapsed = System.nanoTime() - began;
        long bytes = memory.getThreadAllocatedBytes(thread) - allocated;
        long jit = compilation.getTotalCompilationTime() - compiled;
        Snapshot after = snapshot();
        long checksum = BenchmarkThreadedLighting.observe(fixture);
        fixture.snapshot("untimed lifecycle observation");
        System.out.println(prefix + " " + index + " " + elapsed + " " + bytes + " " + jit + " " + checksum
            + " " + (fixture.dispatch.executed - dispatch) + " " + (fixture.light.executed - light));
        long[] deltas = new long[kinds.length];
        for (int i = 0; i < deltas.length; i++) deltas[i] = after.registered()[i] - before.registered()[i];
        System.out.println("LIFECYCLE " + prefix + " " + index + " steps=" + steps
            + " gcCount=" + (after.gcCount() - before.gcCount()) + " gcMs=" + (after.gcMs() - before.gcMs())
            + " pendingBefore=" + before.pending() + " pendingAfter=" + after.pending()
            + " released=" + (after.released() - before.released())
            + " registrations[layer,view,map,mapView]=" + Arrays.toString(deltas));
    }

    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses(); SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        int count = Integer.parseInt(args[0]), steps = Integer.parseInt(args[1]), samples = Integer.parseInt(args[2]);
        var compilation = ManagementFactory.getCompilationMXBean();
        var memory = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            fixture.initialize(); fixture.assertReady(); bind();
            int warm = 0, quiet = 0;
            while (warm < 32 || quiet < 12) {
                if (warm >= 512) throw new AssertionError("Compiler did not settle");
                long before = compilation.getTotalCompilationTime();
                sample(fixture, count, steps, warm, "WARM_SAMPLE", compilation, memory, thread);
                quiet = before == compilation.getTotalCompilationTime() ? quiet + 1 : 0;
                warm++;
            }
            for (int index = 0; index < samples; index++)
                sample(fixture, count, steps, index, "SAMPLE", compilation, memory, thread);
        }
    }
}
