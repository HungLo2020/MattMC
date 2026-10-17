package net.minecraft.world.level.chunk.storage;

import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in real local-filesystem measurement (the temporary directory may be tmpfs), deliberately without timing assertions. */
class IOWorkerThroughputBenchmark {
    @TempDir Path directory;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static int configuredCount(String key, int fallback, int minimum) {
        String value = System.getenv(key);
        int count = value == null ? fallback : Integer.parseInt(value);
        if (count < minimum || count > 100) throw new IllegalArgumentException(key + " outside " + minimum + "..100");
        return count;
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }
    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }
    private static long compilationMillis() {
        var compiler = ManagementFactory.getCompilationMXBean();
        return compiler != null && compiler.isCompilationTimeMonitoringSupported() ? compiler.getTotalCompilationTime() : 0;
    }
    private static long cpuNanos() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean
            ? bean.getProcessCpuTime() : 0;
    }

    @Test void normalAndEncodedSaveFlushClose() throws Exception {
        final int count = 256;
        int warmups = configuredCount("MATTMC_IOWORKER_WARMUPS", 2, 0);
        int trials = configuredCount("MATTMC_IOWORKER_TRIALS", 5, 1);
        var random = new Random(818);
        var tags = new ArrayList<CompoundTag>();
        var tapes = new ArrayList<SerializableChunkData.Encoded>();
        var positions = new ArrayList<ChunkPos>();
        for (int i = 0; i < count; i++) {
            var tag = new CompoundTag();
            tag.putInt("value", i);
            byte[] payload = new byte[4096];
            random.nextBytes(payload);
            tag.putByteArray("payload", payload);
            tags.add(tag);
            tapes.add(new SerializableChunkData.Encoded(NativeNbtRegionAccess.writeTape(tag), () -> tag));
            positions.add(new ChunkPos(i % 32, i / 32));
        }
        var info = new RegionStorageInfo("throughput", null, "chunk");
        // Inputs/order identical in every run. Retain all trials, including GC/JIT outliers.
        System.out.printf("IOWORKER_THROUGHPUT_CONFIG warmups=%d trials=%d writes=%d seed=818 payload_bytes=4096 worker_origin=%s%n",
            warmups, trials, count, IOWorker.class.getProtectionDomain().getCodeSource().getLocation());
        for (int trial = -warmups; trial < trials; trial++) {
            for (boolean encoded : new boolean[]{false, true}) {
                String route = encoded ? "encoded" : "normal";
                Path path = directory.resolve(route + "-" + trial);
                long gcBefore = gcCount();
                long gcMillisBefore = gcMillis();
                long compilationBefore = compilationMillis();
                long cpuBefore = cpuNanos();
                long start = System.nanoTime();
                try (var worker = new IOWorker(info, path, false)) {
                    var writes = new CompletableFuture<?>[count];
                    for (int i = 0; i < count; i++) {
                        var tape = tapes.get(i);
                        writes[i] = encoded ? worker.storeEncoded(positions.get(i), () -> tape)
                            : worker.store(positions.get(i), tags.get(i));
                    }
                    CompletableFuture.allOf(writes).get(60, TimeUnit.SECONDS);
                    worker.synchronize(true).get(60, TimeUnit.SECONDS);
                }
                long nanos = System.nanoTime() - start;
                long cpu = cpuNanos() - cpuBefore;
                long compilation = compilationMillis() - compilationBefore;
                long gcTime = gcMillis() - gcMillisBefore;
                long collections = gcCount() - gcBefore;
                // Reopen checks are outside the measured interval.
                try (var reopened = new IOWorker(info, path, false)) {
                    for (int i : new int[]{0, count / 2, count - 1}) {
                        assertEquals(tags.get(i), reopened.loadAsync(positions.get(i)).get(10, TimeUnit.SECONDS).orElseThrow());
                    }
                }
                if (trial >= 0) {
                    System.out.printf("IOWORKER_THROUGHPUT seed=818 payload_bytes=4096 writes=%d route=%s trial=%d elapsed_ns=%d cpu_ns=%d gc_count=%d gc_ms=%d compilation_ms=%d%n",
                        count, route, trial, nanos, cpu, collections, gcTime, compilation);
                }
            }
        }
    }
}
