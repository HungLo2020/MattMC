package net.minecraft.world.level.chunk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Full resize callers. The same measured VarHandle reset restores the same input in both modes. */
public final class NativePaletteResizeVerification {
    private static volatile long sink;
    private static final VarHandle NATIVE_DATA;
    private static final VarHandle JAVA_DATA;
    static {
        try {
            NATIVE_DATA = MethodHandles.privateLookupIn(PalettedContainer.class, MethodHandles.lookup())
                    .findVarHandle(PalettedContainer.class, "data", PalettedContainer.Data.class);
            JAVA_DATA = MethodHandles.privateLookupIn(JavaPaletteResize.class, MethodHandles.lookup())
                    .findVarHandle(JavaPaletteResize.class, "data", PalettedContainer.Data.class);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private final boolean nativeMode;
    private final boolean trigger;
    private final List<PalettedContainer.Data<BlockState>> inputs = new ArrayList<>();
    private final List<Integer> requested = new ArrayList<>();
    private final List<PalettedContainer<BlockState>> nativeContainers = new ArrayList<>();
    private final List<JavaPaletteResize<BlockState>> javaContainers = new ArrayList<>();
    private final BlockState added = Block.BLOCK_STATE_REGISTRY.byId(31800);

    private NativePaletteResizeVerification(boolean nativeMode, String name) {
        this.nativeMode = nativeMode;
        trigger = name.startsWith("trigger_");
        var sources = new ArrayList<PalettedContainer<BlockState>>();
        if (name.startsWith("recorded_")) {
            var dimension = name.substring(9);
            for (var record : PalettePackingFixtures.records())
                if (record.get("dimension").getAsString().equals(dimension))
                    sources.addAll(PalettePackingFixtures.recorded(record));
            sources.removeIf(source -> source.dataForNativeScan().storage().getBits() > 8);
        } else {
            int n = Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));
            String pattern = name.contains("cycle") ? "cycle" : name.contains("runs") ? "runs" : "random";
            for (int seed = 0; seed < 16; seed++) {
                var source = PalettePackingFixtures.states(n, 1937L + seed * 137L, pattern);
                if (name.startsWith("stale_")) {
                    var state = source.get(0, 0, 0);
                    for (int i = 0; i < 4096; i++)
                        source.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15, state);
                }
                sources.add(source);
            }
        }
        if (sources.isEmpty()) throw new AssertionError("Empty workload: " + name);
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (var source : sources) {
            var input = source.dataForNativeScan();
            int bits = input.storage().getBits() + 1;
            try {
                if (!NativePaletteResize.copy(owner, input, NativePaletteResizeTest.fresh(owner, bits)))
                    throw new AssertionError("Native slice bypassed: " + name);
            } catch (Exception e) { throw new AssertionError(e); }
            inputs.add(input); requested.add(bits);
            var nativeInput = input.copy();
            nativeContainers.add(PalettePackingFixtures.custom(owner, nativeInput.configuration(), nativeInput.storage(), nativeInput.palette()));
            javaContainers.add(new JavaPaletteResize<>(owner, input.copy()));
            // Prove the actual outer write triggers growth and compare full results before timing.
            if (trigger) {
                var a = javaContainers.getLast(); var b = nativeContainers.getLast();
                if (a.getAndSetUnchecked(0, 0, 0, added) != b.getAndSetUnchecked(0, 0, 0, added))
                    throw new AssertionError("Previous state differs");
                if (a.data.storage().getBits() <= input.storage().getBits())
                    throw new AssertionError("No growth: " + name);
                NativePaletteResizeTest.equal(a.data, b.dataForNativeScan());
            }
        }
        System.out.println("RESIZE_WORKLOAD name=" + name + " sections=" + sources.size());
    }

    private long run(int repeats) {
        long sum = 0, start = System.nanoTime();
        for (int r = 0; r < repeats; r++) {
            for (int i = 0; i < inputs.size(); i++) {
                PalettedContainer.Data<BlockState> output;
                int returned;
                if (nativeMode) {
                    var container = nativeContainers.get(i);
                    NATIVE_DATA.setVolatile(container, trigger ? inputs.get(i).copy() : inputs.get(i));
                    returned = trigger ? Block.BLOCK_STATE_REGISTRY.getId(container.getAndSetUnchecked(0, 0, 0, added))
                            : container.onResize(requested.get(i), added);
                    output = container.dataForNativeScan();
                } else {
                    var container = javaContainers.get(i);
                    JAVA_DATA.setVolatile(container, trigger ? inputs.get(i).copy() : inputs.get(i));
                    returned = trigger ? Block.BLOCK_STATE_REGISTRY.getId(container.getAndSetUnchecked(0, 0, 0, added))
                            : container.onResize(requested.get(i), added);
                    output = container.data;
                }
                // Consume every output word, final palette size and returned ID/state.
                // These are fresh results, never a precomputed/cached remapping.
                long hash = returned * 31L + output.palette().getSize();
                for (long word : output.storage().getRaw()) hash = hash * 31 + word;
                sum += hash;
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
                ns[round] = (double) elapsed / repeats / inputs.size();
            }
            if (accepted) {
                run(1);
                System.out.println(
                        "RESIZE_BENCH name="
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
        NativePaletteResizeTest.bootstrap();
        var names = new ArrayList<String>();
        for (String pattern : List.of("random", "cycle", "runs"))
            for (int n : new int[] {1, 16, 32, 64, 128, 256})
                names.add("grow_" + pattern + "_" + n);
        for (int n : new int[] {1, 16, 32, 64, 128, 256}) names.add("trigger_random_" + n);
        names.addAll(List.of("stale_16", "stale_256", "stale_128"));
        for (String dim : List.of("overworld", "nether", "end", "primordial")) names.add("recorded_" + dim);
        for (var name : names)
            if (args.length < 2 || args[1].equals("all") || args[1].equals(name))
                new NativePaletteResizeVerification(args[0].equals("native"), name).bench(name);
    }
}
