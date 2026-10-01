package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Complete original/migrated unpack callers with fresh streams, words, containers and no result cache. */
public final class NativePaletteUnpackingVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final List<NativePaletteUnpackingTest.Input> inputs = new ArrayList<>();
    private final Strategy<BlockState> owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
    private NativePaletteUnpackingVerification(boolean nativeMode, String name) {
        this.nativeMode = nativeMode;
        if (name.startsWith("edited_")) {
            var dimension = name.substring(7);
            for (var record : PalettePackingFixtures.records()) {
                if (!record.get("dimension").getAsString().equals(dimension)) continue;
                var section = PalettePackingFixtures.recorded(record).getFirst().copy();
                for (int i=0;i<768;i++) section.getAndSetUnchecked(i&15,i>>8,i>>4&15,Block.BLOCK_STATE_REGISTRY.byId(i));
                var packed=JavaPalettePacking.pack(section,owner);
                inputs.add(new NativePaletteUnpackingTest.Input(ImmutableList.copyOf(packed.paletteEntries()),
                        packed.storage().orElseThrow().toArray(),packed.bitsPerEntry()));
            }
        } else {
            int n = Integer.parseInt(name.substring(name.lastIndexOf('_')+1));
            String pattern = name.contains("cycle")?"cycle":name.contains("runs")?"runs":name.contains("stale")?"stale":"random";
            String list = name.startsWith("arrays_")?"arrays":name.startsWith("guava_")?"guava":name.startsWith("arraylist_")?"arraylist":"list";
            for(int seed=0;seed<8;seed++) inputs.add(NativePaletteUnpackingTest.input(n,1977L+seed*137L,pattern,list));
        }
        if(inputs.isEmpty())throw new AssertionError("Empty workload: "+name);
        for(var input:inputs) NativePaletteUnpackingTest.compare(input,true);
        System.out.println("UNPACK_WORKLOAD name="+name+" sections="+inputs.size());
    }
    private long run(int repeats) {
        long sum=0,start=System.nanoTime();
        for(int r=0;r<repeats;r++) for(var input:inputs) {
            var packed=input.packed();
            var container=(nativeMode?PalettedContainer.unpack(owner,packed):JavaPaletteUnpacking.unpack(owner,packed)).result().orElseThrow();
            var output=container.dataForNativeScan();
            long hash=output.storage().getBits()*31L+output.palette().getSize();
            for(long word:output.storage().getRaw())hash=hash*31+word;
            sum+=hash;
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
                ns[round] = (double) elapsed / repeats / inputs.size();
            }
            if (accepted) {
                run(1);
                System.out.println(
                        "UNPACK_BENCH name="
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
        for(int n:new int[]{257,512,513,1024,2048,4096,8192,16384,31809,32769,65536})names.add("random_"+n);
        for(String pattern:List.of("cycle","runs"))for(int n:new int[]{257,513,4096})names.add(pattern+"_"+n);
        for(String list:List.of("arrays","guava","arraylist"))names.add(list+"_random_513");
        for(int n:new int[]{4096,65536})names.add("stale_"+n);
        for(String dim:List.of("overworld","nether","end","primordial"))names.add("edited_"+dim);
        return names;
    }
    public static void main(String[] args) {
        NativePaletteUnpackingTest.bootstrap();
        for(var name:names())if(args.length<2||args[1].equals("all")||args[1].equals(name))
            new NativePaletteUnpackingVerification(args[0].equals("native"),name).bench(name);
    }
}
