package net.minecraft.world.level.chunk;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Consumer;

/** Complete production getAll including copies, FFM, lookups and ordered consumption. */
public final class NativePaletteDistinctVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final List<PalettedContainer<Object>> sources=new ArrayList<>();
    private final Accumulator accumulator=new Accumulator();
    private static final class Accumulator implements Consumer<Object> {
        long sum;
        @Override public void accept(Object value) { sum=sum*31+PaletteDistinctFixtures.identityCode(value); }
    }
    @SuppressWarnings("unchecked")
    private void add(PalettedContainer<?> source) { sources.add((PalettedContainer<Object>)(Object)source); }
    private NativePaletteDistinctVerification(boolean nativeMode,String name) {
        this.nativeMode=nativeMode;
        if(name.startsWith("saved_biomes_")) {
            for(var source:PaletteDistinctFixtures.recorded(name.substring(13)))
                if(source.dataForNativeScan().storage().getBits()!=0)add(source);
        } else if(name.startsWith("saved_blocks_")) {
            for(var record:PalettePackingFixtures.records())if(record.get("dimension").getAsString().equals(name.substring(13)))
                for(var source:PalettePackingFixtures.recorded(record))
                    if(source.dataForNativeScan().storage().getBits()!=0)add(source);
        } else {
            int cardinality=Integer.parseInt(name.substring(name.lastIndexOf('_')+1));
            String pattern=name.contains("runs")?"runs":name.contains("cycle")?"cycle":"random";
            for(int seed=0;seed<16;seed++) {
                PalettedContainer<?> source=name.startsWith("biome")
                    ?PaletteDistinctFixtures.biomes(cardinality,1977L+137L*seed,pattern)
                    :PalettePackingFixtures.states(cardinality,1977L+137L*seed,pattern);
                if(name.contains("stale"))fill(source);
                add(source);
            }
        }
        if(sources.isEmpty())throw new AssertionError("No migrated sources: "+name);
        for(var source:sources) {
            PaletteDistinctFixtures.compare(source);
            try(var ids=NativePaletteDistinct.scan(source.dataForNativeScan().storage())) {
                if(ids==null)throw new AssertionError("Rust bypass: "+name);
            }
        }
        System.out.println("DISTINCT_WORKLOAD name="+name+" sections="+sources.size()+" sizes="+
            sources.stream().map(s->s.dataForNativeScan().storage().getSize()).distinct().toList());
    }
    private static <T> void fill(PalettedContainer<T> source) {
        var storage=source.dataForNativeScan().storage();
        // Uniform input keeps its existing palette/width; all fields are real ID zero.
        Arrays.fill(storage.getRaw(),0L);
    }
    private long run(int repeats) {
        long sum=0,start=System.nanoTime();
        for(int r=0;r<repeats;r++)for(var source:sources) {
            accumulator.sum=0;
            if(nativeMode)source.getAll(accumulator);else JavaPaletteDistinct.getAll(source,accumulator);
            sum+=accumulator.sum;
        }
        sink=sum;return System.nanoTime()-start;
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
                        "DISTINCT_BENCH name="
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
    public static List<String> names() {
        var names=new ArrayList<String>();
        for(int n:new int[]{2,3,4,5,8,16,64})names.add("biome_random_"+n);
        names.addAll(List.of("biome_runs_2","biome_runs_8","biome_cycle_8","biome_stale_2","biome_stale_8"));
        for(int n:new int[]{2,3,16,17,64,256,4096})names.add("block_random_"+n);
        names.addAll(List.of("block_stale_16","block_stale_256","saved_biomes_overworld","saved_biomes_primordial",
            "saved_blocks_overworld","saved_blocks_nether","saved_blocks_end","saved_blocks_primordial"));
        return names;
    }
    public static void main(String[] args) {
        NativePaletteDistinctTest.bootstrap();
        for(var name:names())if(args.length<2||args[1].equals("all")||args[1].equals(name))
            new NativePaletteDistinctVerification(args[0].equals("native"),name).bench(name);
    }
}
