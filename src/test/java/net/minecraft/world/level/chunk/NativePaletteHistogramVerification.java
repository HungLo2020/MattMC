package net.minecraft.world.level.chunk;

import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Complete histogram callback delivery and real section recount, with matching inputs. */
public final class NativePaletteHistogramVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final boolean sectionMode;
    private final List<PalettedContainer<BlockState>> sources = new ArrayList<>();
    private final List<LevelChunkSection> nativeSections = new ArrayList<>();
    private final List<JavaSectionCounts> javaSections = new ArrayList<>();
    private final Accumulator accumulator = new Accumulator();

    private static final class Accumulator implements PalettedContainer.CountConsumer<BlockState> {
        long sum;

        public void accept(BlockState state, int count) {
            sum = (sum * 31 + Block.BLOCK_STATE_REGISTRY.getId(state)) * 31 + count;
        }
    }

    private NativePaletteHistogramVerification(boolean nativeMode, String name) {
        this.nativeMode = nativeMode;
        sectionMode = name.startsWith("recalc_");
        if (name.contains("recorded_")) {
            String dimension = name.substring(name.indexOf("recorded_") + 9);
            for (var record : PalettePackingFixtures.records())
                if (record.get("dimension").getAsString().equals(dimension))
                    sources.addAll(PalettePackingFixtures.recorded(record));
            // Single-entry shortcut is outside this migration and is not credited as native work.
            sources.removeIf(source -> source.dataForNativeScan().palette().getSize() == 1);
        } else if (name.equals("hist_collisions")) {
            var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
            var config = owner.getConfigurationForBitCount(15);
            var ids = new ArrayList<Integer>();
            for (int id = 1; id < Block.BLOCK_STATE_REGISTRY.size() && ids.size() < 24; id++) {
                int h = id * 0x9e3779b9;
                if (((h ^ (h >>> 16)) & 31) == 0) ids.add(id);
            }
            for (int seed = 0; seed < 16; seed++) {
                var random = new Random(seed);
                int[] values = new int[4096];
                for (int i = 0; i < values.length; i++)
                    values[i] = ids.get(random.nextInt(ids.size()));
                sources.add(
                        PalettePackingFixtures.custom(
                                owner,
                                config,
                                new SimpleBitStorage(config.bitsInMemory(), 4096, values),
                                owner.globalPalette()));
            }
        } else {
            int cardinality = Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));
            for (int seed = 0; seed < 16; seed++) {
                var source =
                        PalettePackingFixtures.states(
                                cardinality,
                                1937L + 137L * seed,
                                name.contains("cycle") ? "cycle" : "random");
                if (name.startsWith("stale_")) {
                    var state =
                            name.startsWith("stale_zero_")
                                    ? Block.BLOCK_STATE_REGISTRY.byId(71)
                                    : source.get(0, 0, 0);
                    for (int i = 0; i < 4096; i++)
                        source.getAndSetUnchecked(i & 15, i >> 8, (i >> 4) & 15, state);
                }
                sources.add(source);
            }
        }
        if (sources.isEmpty()) throw new AssertionError("Empty workload: " + name);
        for (var source : sources) {
            if (source.dataForNativeScan().palette().getSize() == 1)
                throw new AssertionError("Outside native slice: " + name);
            if (nativeMode)
                try (var counts =
                        NativePaletteHistogram.scan(source.dataForNativeScan().storage())) {
                    if (counts == null) throw new AssertionError("Rust bypassed: " + name);
                }
            if (sectionMode) {
                if (nativeMode) nativeSections.add(new LevelChunkSection(source, null));
                else javaSections.add(new JavaSectionCounts(source));
            }
        }
        System.out.println("HISTOGRAM_WORKLOAD name=" + name + " sections=" + sources.size());
    }

    private long run(int repeats) {
        long sum = 0, start = System.nanoTime();
        for (int r = 0; r < repeats; r++)
            for (int i = 0; i < sources.size(); i++) {
                if (sectionMode) {
                    if (nativeMode) {
                        var section = nativeSections.get(i);
                        section.recalcBlockCounts();
                        sum +=
                                (section.hasOnlyAir() ? 1 : 0)
                                        | (section.isRandomlyTickingBlocks() ? 2 : 0)
                                        | (section.isRandomlyTickingFluids() ? 4 : 0);
                    } else {
                        var section = javaSections.get(i);
                        section.recalcBlockCounts();
                        sum +=
                                (section.hasOnlyAir() ? 1 : 0)
                                        | (section.isRandomlyTickingBlocks() ? 2 : 0)
                                        | (section.isRandomlyTickingFluids() ? 4 : 0);
                    }
                } else {
                    accumulator.sum = 0;
                    if (nativeMode) sources.get(i).count(accumulator);
                    else JavaPaletteHistogram.count(sources.get(i), accumulator);
                    sum += accumulator.sum;
                }
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
                        "HISTOGRAM_BENCH name="
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

    public static void main(String[] args) {
        NativePaletteHistogramTest.bootstrap();
        boolean nativeMode = args[0].equals("native");
        var names = new ArrayList<String>();
        for (int n : PalettePackingFixtures.CARDINALITIES)
            if (n != 1) names.add("hist_random_" + n);
        names.addAll(
                List.of(
                        "hist_cycle_4096",
                        "hist_collisions",
                        "stale_16",
                        "stale_256",
                        "stale_512",
                        "stale_zero_256"));
        for (var mode : List.of("hist", "recalc"))
            for (var dim : List.of("overworld", "nether", "end", "primordial"))
                names.add(mode + "_recorded_" + dim);
        for (var name : names)
            if (args.length < 2 || args[1].equals("all") || args[1].equals(name))
                new NativePaletteHistogramVerification(nativeMode, name).bench(name);
    }
}
