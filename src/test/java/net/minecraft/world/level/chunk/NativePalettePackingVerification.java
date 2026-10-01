package net.minecraft.world.level.chunk;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Equal real packed-section inputs; times the complete caller and consumes raw output. */
public final class NativePalettePackingVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final List<PalettedContainer<net.minecraft.world.level.block.state.BlockState>>
            sources = new ArrayList<>();
    private final List<Strategy<net.minecraft.world.level.block.state.BlockState>> strategies =
            new ArrayList<>();

    private NativePalettePackingVerification(boolean nativeMode, String name) {
        this.nativeMode = nativeMode;
        if (name.startsWith("recorded_")) {
            for (var record : PalettePackingFixtures.records())
                if (record.get("dimension").getAsString().equals(name.substring(9)))
                    sources.addAll(PalettePackingFixtures.recorded(record));
        } else {
            int cardinality = Integer.parseInt(name.substring(name.indexOf('_') + 1));
            for (int i = 0; i < 16; i++) {
                var source = PalettePackingFixtures.states(cardinality, 1937L + i * 137, "random");
                if (name.startsWith("stale_")) {
                    var value = source.get(0, 0, 0);
                    for (int k = 0; k < 4096; k++)
                        source.getAndSetUnchecked(k & 15, k >> 8, (k >> 4) & 15, value);
                }
                sources.add(source);
            }
        }
        for (var source : sources) {
            var strategy = PalettePackingFixtures.owner(source);
            strategies.add(strategy);
            if (nativeMode && PalettePackingFixtures.nativePack(source, strategy) == null)
                throw new AssertionError("Benchmark bypasses Rust: " + name);
        }
    }

    private long run(int repeats) {
        long sum = 0, start = System.nanoTime();
        for (int r = 0; r < repeats; r++)
            for (int i = 0; i < sources.size(); i++) {
                var source = sources.get(i);
                var strategy = strategies.get(i);
                var result =
                        nativeMode
                                ? source.pack(strategy)
                                : JavaPalettePacking.pack(source, strategy);
                sum += PalettePackingFixtures.checksum(result);
            }
        sink = sum;
        return System.nanoTime() - start;
    }

    private static double median(long[] a) {
        a = a.clone();
        Arrays.sort(a);
        return a[a.length / 2];
    }

    private void bench(String name) {
        var jit = ManagementFactory.getCompilationMXBean();
        int repeats = 1;
        for (int attempt = 0; attempt < 6; attempt++) {
            long started = System.nanoTime();
            long[] recent = new long[10], compilation = new long[10];
            boolean stable = false;
            for (int round = 0; round < 300; round++) {
                long before = jit.getTotalCompilationTime(), elapsed = run(repeats);
                if (elapsed < 120_000_000) {
                    repeats *= Math.max(2, (int) Math.ceil(120_000_000.0 / Math.max(elapsed, 1)));
                    round = -1;
                    continue;
                }
                recent[round % 10] = elapsed;
                compilation[round % 10] = jit.getTotalCompilationTime() - before;
                if (round >= 9
                        && System.nanoTime() - started > 3_000_000_000L
                        && Arrays.stream(compilation).sum() == 0) {
                    long[] a = new long[5], b = new long[5];
                    for (int i = 0; i < 5; i++) {
                        a[i] = recent[(round - i) % 10];
                        b[i] = recent[(round - i - 5) % 10];
                    }
                    if (Math.abs(median(a) / median(b) - 1) < .05) {
                        stable = true;
                        break;
                    }
                }
            }
            if (!stable) throw new AssertionError("Unstable warmup: " + name);
            double[] ns = new double[11];
            long[] js = new long[11];
            boolean accepted = true;
            for (int round = 0; round < 11; round++) {
                long before = jit.getTotalCompilationTime(), elapsed = run(repeats);
                js[round] = jit.getTotalCompilationTime() - before;
                if (js[round] != 0) {
                    accepted = false;
                    break;
                }
                ns[round] = (double) elapsed / repeats / sources.size();
            }
            if (accepted) {
                run(1);
                System.out.println(
                        "PALETTE_BENCH name="
                                + name
                                + " repeats="
                                + repeats
                                + " ns="
                                + Arrays.toString(ns)
                                + " jit="
                                + Arrays.toString(js)
                                + " checksum="
                                + sink);
                return;
            }
        }
        throw new AssertionError("Late compilation: " + name);
    }

    public static void main(String[] args) throws Exception {
        NativePalettePackingTest.bootstrap();
        boolean nativeMode = args[0].equals("native");
        var names = new ArrayList<String>();
        for (int n : PalettePackingFixtures.CARDINALITIES) names.add("random_" + n);
        names.addAll(
                List.of(
                        "stale_16",
                        "stale_256",
                        "stale_512",
                        "recorded_overworld",
                        "recorded_nether",
                        "recorded_end",
                        "recorded_primordial"));
        for (var name : names) {
            if (args.length > 1 && !args[1].equals("all") && !args[1].equals(name)) continue;
            new NativePalettePackingVerification(nativeMode, name).bench(name);
        }
    }
}
