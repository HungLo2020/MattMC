package net.minecraft.world.level.levelgen.blending;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Whole production grid fill: collection, copies, ordinary FFM and output writes. */
public final class NativeBlendingVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final List<BlendingFixtures.Grid> fixtures = new ArrayList<>();
    private final List<Blender> production = new ArrayList<>();
    private final List<JavaBlender> original = new ArrayList<>();
    private final double[] alpha, offset;
    private NativeBlendingVerification(boolean nativeMode, String name) {
        this.nativeMode = nativeMode;
        for(int seed=0;seed<8;seed++) {
            var grid=BlendingFixtures.grid(name,1977L+137L*seed);fixtures.add(grid);
            production.add(grid.production());original.add(grid.original());
            NativeBlendingTest.compare(grid);
        }
        int n=fixtures.getFirst().size()*fixtures.getFirst().size();
        alpha=new double[n];offset=new double[n];
        for(int i=0;i<fixtures.size();i++) {
            var grid=fixtures.get(i);
            if(!NativeBlending.fill(production.get(i),grid.data(),grid.x(),grid.z(),grid.size(),alpha,offset))
                throw new AssertionError("Native bypass: "+name);
        }
        System.out.println("BLENDING_WORKLOAD name="+name+" fixtures="+fixtures.size()+" queries="+n
            +" chunks="+fixtures.getFirst().data().size());
    }
    private long run(int repeats) {
        long start=System.nanoTime(), sum=0;
        for(int r=0;r<repeats;r++)for(int i=0;i<fixtures.size();i++) {
            var grid=fixtures.get(i);
            if(nativeMode)production.get(i).fillBlendingOutputs(grid.x(),grid.z(),grid.size(),alpha,offset);
            else BlendingFixtures.original(original.get(i),grid.x(),grid.z(),grid.size(),alpha,offset);
            long hash=0;
            for(int q=0;q<alpha.length;q++) {
                hash=hash*31+Double.doubleToRawLongBits(alpha[q]);
                hash=hash*31+Double.doubleToRawLongBits(offset[q]);
            }
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
                        && System.nanoTime() - started > 6_000_000_000L
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
                ns[round] = (double) elapsed / repeats / fixtures.size();
            }
            if (accepted) {
                run(1);
                System.out.println(
                        "BLENDING_BENCH name="
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
        NativeBlendingTest.bootstrap();
        for(var name:BlendingFixtures.names())if(args.length<2||args[1].equals("all")||args[1].equals(name))
            new NativeBlendingVerification(args[0].equals("native"),name).bench(name);
    }
}
