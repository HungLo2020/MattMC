package net.minecraft.world.level.lighting;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Measure the actual chunk-distance graph with each queue backend, including its callers. */
public final class NativeLeveledGraphVerification {
    static volatile long sink;
    final String name;
    final boolean nativeMode;
    final QueueTranscript.Call[] calls;
    final QueueTranscript.NativeGraph nativeGraph = new QueueTranscript.NativeGraph();
    final QueueTranscript.OriginalGraph javaGraph = new QueueTranscript.OriginalGraph();

    NativeLeveledGraphVerification(boolean nativeMode, String name) throws java.security.NoSuchAlgorithmException {
        this.nativeMode = nativeMode;
        this.name = name;
        this.calls = QueueTranscript.record(name.substring(6));
        long[] mix = new long[9];
        for (var call : calls) mix[call.operation()]++;
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var bytes = java.nio.ByteBuffer.allocate(32);
        for (var call : calls) {
            bytes.clear();
            bytes.putInt(call.operation()).putLong(call.value()).putInt(call.level()).putInt(call.bound()).putInt(call.target()).putLong(call.output());
            digest.update(bytes.array());
        }
        System.out.println("LIGHT_QUEUE_FIXTURE case=" + name + " calls=" + calls.length + " mix=" + Arrays.toString(mix)
            + " trace=" + java.util.HexFormat.of().formatHex(digest.digest()) + " native_operations=" + (mix[0]+mix[1]+mix[2]+mix[6]+mix[7]+mix[8])
            + " java_operations=" + QueueTranscript.originalMutationCount(calls));
        validate();
    }

    void validate() {
        QueueTranscript.exercise(javaGraph, name.substring(6));
        QueueTranscript.exercise(nativeGraph, name.substring(6));
        if (!Arrays.equals(javaGraph.values(), nativeGraph.values())
            || !javaGraph.events().equals(nativeGraph.events())
            || javaGraph.work() || nativeGraph.work()
            || javaGraph.queueSize() != 0 || nativeGraph.queueSize() != 0) throw new AssertionError("Graph output mismatch");
        javaGraph.events().clear(); nativeGraph.events().clear();
    }

    long run(int repeats) {
        long sum = 0;
        String workload = name.substring(6);
        long start = System.nanoTime();
        if (nativeMode) {
            for (int repeat = 0; repeat < repeats; repeat++) {
                QueueTranscript.exercise(nativeGraph, workload);
                for (var update : nativeGraph.events()) sum = sum * 31 + update.position() + update.level();
                nativeGraph.events().clear();
            }
        } else {
            for (int repeat = 0; repeat < repeats; repeat++) {
                QueueTranscript.exercise(javaGraph, workload);
                for (var update : javaGraph.events()) sum = sum * 31 + update.position() + update.level();
                javaGraph.events().clear();
            }
        }
        sink = sum;
        return System.nanoTime() - start;
    }

    static double median(long[] values) {
        values = values.clone(); Arrays.sort(values); return values[values.length / 2];
    }

    void benchmark(boolean quick) {
        var jit = ManagementFactory.getCompilationMXBean();
        var threads = ManagementFactory.getThreadMXBean();
        if (!threads.isCurrentThreadCpuTimeSupported()) throw new AssertionError("Thread CPU timing unavailable");
        threads.setThreadCpuTimeEnabled(true);
        long minimum = quick ? 25_000_000L : 200_000_000L;
        int repeats = 1;
        System.gc(); System.gc();
        for (int attempt = 0; attempt < 6; attempt++) {
            long started = System.nanoTime();
            long[] times = new long[10], compilations = new long[10];
            boolean stable = false;
            for (int round = 0; round < 300; round++) {
                long before = jit.getTotalCompilationTime(), cpuStarted = threads.getCurrentThreadCpuTime(), elapsed = run(repeats);
                long cpuElapsed = threads.getCurrentThreadCpuTime() - cpuStarted;
                if (elapsed < minimum) {
                    repeats *= Math.max(2, (int)Math.ceil((double)minimum / Math.max(1, elapsed)));
                    round = -1; continue;
                }
                times[round % 10] = elapsed;
                compilations[round % 10] = jit.getTotalCompilationTime() - before;
                if (round >= 9 && System.nanoTime() - started > (quick ? 1_000_000_000L : 15_000_000_000L)
                    && Arrays.stream(compilations).sum() == 0) {
                    long[] a = new long[5], b = new long[5];
                    for (int k = 0; k < 5; k++) { a[k] = times[(round-k)%10]; b[k] = times[(round-k-5)%10]; }
                    if (Math.abs(median(a) / median(b) - 1) < .05) { stable = true; break; }
                }
            }
            if (!stable) throw new AssertionError("Warmup unstable");
            long warmup = System.nanoTime() - started;
            double[] ns = new double[quick ? 5 : 11];
            double[] cpuNs = new double[ns.length];
            long[] compilationsDuringSamples = new long[ns.length];
            boolean accepted = true;
            for (int round = 0; round < ns.length; round++) {
                long before = jit.getTotalCompilationTime(), cpuStarted = threads.getCurrentThreadCpuTime(), elapsed = run(repeats);
                long cpuElapsed = threads.getCurrentThreadCpuTime() - cpuStarted;
                compilationsDuringSamples[round] = jit.getTotalCompilationTime() - before;
                if (compilationsDuringSamples[round] != 0) { accepted = false; break; }
                ns[round] = (double)elapsed / repeats;
                cpuNs[round] = (double)cpuElapsed / repeats;
            }
            if (accepted) {
                validate(); run(1);
                System.out.println("LIGHT_QUEUE_BENCH case=" + name + " warmup_ns=" + warmup + " repeats=" + repeats
                    + " ns=" + Arrays.toString(ns) + " jit=" + Arrays.toString(compilationsDuringSamples) + " checksum=" + sink + " cpu_ns=" + Arrays.toString(cpuNs));
                return;
            }
        }
        throw new AssertionError("Late compilation");
    }

    public static void main(String[] args) throws Throwable {
        if (!Arrays.asList("graph_single_ticket", "graph_overlapping_tickets", "graph_ticket_churn", "graph_single_ticket_incremental", "graph_overlapping_tickets_incremental", "graph_ticket_churn_incremental").contains(args[1])) throw new IllegalArgumentException("Unknown case");
        QueueTranscript.bootstrap();
        var benchmark = new NativeLeveledGraphVerification(args[0].equals("native"), args[1]);
        LightingQueueAffinity.pin();
        benchmark.benchmark(Arrays.asList(args).contains("quick"));
    }
}
