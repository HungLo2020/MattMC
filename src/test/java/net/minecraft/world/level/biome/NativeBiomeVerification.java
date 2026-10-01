package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Pair;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Opt-in original-Java oracle and direct lookup timings. No density/terrain
 * arithmetic is timed. Native timings include packing, FFM and result handling. */
public final class NativeBiomeVerification {
    public interface Oracle { int find(long[] coordinates); }
    public interface OracleFactory { Oracle create(long[][] boxes); }
    private static volatile long sink;
    private static Climate.Sampler batchGuardSampler;

    static Climate.ParameterList<Integer> list(long[][] boxes) {
        var rows = new ArrayList<Pair<Climate.ParameterPoint, Integer>>();
        for (int i = 0; i < boxes.length; i++) {
            long[] b = boxes[i];
            rows.add(Pair.of(new Climate.ParameterPoint(new Climate.Parameter(b[0], b[1]),
                new Climate.Parameter(b[2], b[3]), new Climate.Parameter(b[4], b[5]),
                new Climate.Parameter(b[6], b[7]), new Climate.Parameter(b[8], b[9]),
                new Climate.Parameter(b[10], b[11]), b[12]), i));
        }
        return new Climate.ParameterList<>(rows);
    }
    static long[][] boxes(Climate.ParameterList<?> list) {
        long[][] out = new long[list.values().size()][13];
        for (int i = 0; i < out.length; i++) {
            var p = list.values().get(i).getFirst().parameterSpace();
            for (int j = 0; j < 6; j++) { out[i][2*j] = p.get(j).min(); out[i][2*j+1] = p.get(j).max(); }
            out[i][12] = p.get(6).min();
        }
        return out;
    }
    static Climate.TargetPoint point(long[] p) { return new Climate.TargetPoint(p[0],p[1],p[2],p[3],p[4],p[5]); }
    static Map<String, long[][]> presets() {
        var result = new TreeMap<String, long[][]>();
        MultiNoiseBiomeSourceParameterList.knownPresets().forEach((k,v) -> result.put(k.id().getPath(), boxes(v)));
        return result;
    }
    static long[] coordinates(Random random, long[][] boxes, int index) {
        long[] out = new long[6];
        long[] box = boxes[random.nextInt(boxes.length)];
        for (int d = 0; d < 6; d++) {
            out[d] = switch (index % 8) {
                case 0 -> box[d*2];
                case 1 -> box[d*2+1];
                case 2 -> box[d*2] - 1;
                case 3 -> box[d*2+1] + 1;
                case 4 -> random.nextLong();
                case 5 -> random.nextBoolean() ? Long.MIN_VALUE : Long.MAX_VALUE;
                default -> random.nextInt(60001) - 30000;
            };
        }
        return out;
    }
    private static void parity() throws Exception {
        OracleFactory factory = (OracleFactory)Class.forName("net.minecraft.world.level.biome.OriginalClimateOracle").getConstructor().newInstance();
        Map<String, long[][]> datasets = presets();
        Random r = new Random(0x51eed);
        for (int size : new int[]{1,2,5,6,7,35,36,37,216,217,1024}) {
            long[][] b = new long[size][13];
            for (long[] row : b) for (int d = 0; d < 6; d++) {
                row[d*2] = r.nextInt(40001)-20000; row[d*2+1] = row[d*2]+r.nextInt(4000);
                row[12] = r.nextInt(10001);
            }
            datasets.put("synthetic-"+size,b);
        }
        datasets.put("ties", new long[][]{new long[13],new long[13],new long[13]});
        long count = 0;
        for (var entry : datasets.entrySet()) {
            long[][] b = entry.getValue();
            for (long seed : new long[]{0,42,-123456789,Long.MIN_VALUE,Long.MAX_VALUE}) {
                Oracle oracle = factory.create(b);
                var nativeList = list(b);
                var scalar = list(b);
                Random random = new Random(seed);
                for (int group = 0; group < 1000; group++) {
                    int n = new int[]{1,2,7,63,64,65,127}[group%7];
                    Climate.TargetPoint[] targets = new Climate.TargetPoint[n];
                    Integer[] expected = new Integer[n], actual = new Integer[n];
                    for (int i = 0; i < n; i++) {
                        long[] p = coordinates(random,b,group*n+i);
                        targets[i] = point(p);
                        expected[i] = oracle.find(p);
                        if (!expected[i].equals(scalar.findValue(targets[i]))) throw new AssertionError("Scalar mismatch "+entry.getKey());
                    }
                    nativeList.findValues(targets, actual);
                    if (!Arrays.equals(expected,actual)) throw new AssertionError("Batch mismatch "+entry.getKey()+" seed="+seed+" group="+group);
                    count += n;
                }
            }
            System.out.println("BIOME_PARITY dataset="+entry.getKey()+" decisions="+count);
        }
        System.out.println("BIOME_PARITY_COMPLETE decisions="+count+" exact=true");
    }

    private static Climate.TargetPoint[][] corpus(long[][] boxes, boolean coherent) {
        Random random = new Random(42);
        Climate.TargetPoint[][] result = new Climate.TargetPoint[64][64];
        long[] p = new long[6];
        for (int group = 0; group < result.length; group++) {
            long[] box = boxes[random.nextInt(boxes.length)];
            for (int d = 0; d < 6; d++) p[d] = (box[d*2]+box[d*2+1])/2;
            for (int i = 0; i < 64; i++) {
                for (int d = 0; d < 6; d++) p[d] = coherent ? p[d]+random.nextInt(65)-32 : random.nextInt(30001)-15000;
                result[group][i] = point(p);
            }
        }
        return result;
    }
    private static Climate.TargetPoint[][] seededCorpus(String name) throws Exception {
        var path=java.nio.file.Path.of(System.getProperty("biome.corpus.dir"),name+".bin");
        long count=java.nio.file.Files.size(path)/48;
        if(count<64 || java.nio.file.Files.size(path)%48!=0)throw new AssertionError("Invalid seeded corpus: "+path);
        var values=new ArrayList<Climate.TargetPoint>();
        try(var input=new java.io.DataInputStream(java.nio.file.Files.newInputStream(path))) {
            for(int i=0;i<count;i++) values.add(new Climate.TargetPoint(input.readLong(),input.readLong(),input.readLong(),input.readLong(),input.readLong(),input.readLong()));
        }
        var points=new Climate.TargetPoint[64][64];
        for(int row=0;row<64;row++)for(int i=0;i<64;i++)points[row][i]=values.get((row*64+i)%values.size());
        return points;
    }
    private static long run(Climate.ParameterList<Integer> tree, Climate.TargetPoint[][] inputs, int repeats, boolean nativeMode, boolean scalar) {
        long hash = 0, start = System.nanoTime();
        for (int r = 0; r < repeats; r++) for (var row : inputs) {
            if (nativeMode && !scalar) {
                // Charge the native route for additional batch arrays too. Original
                // Java consumes samples individually and needs neither array.
                if (!net.minecraft.world.level.levelgen.BiomeSamplerSafety.canBatch(batchGuardSampler)) throw new AssertionError();
                var packed = row.clone();
                Integer[] out = new Integer[row.length];
                tree.findValues(packed, out);
                for (Integer value : out) hash = hash * 31 + value;
            } else {
                for (var point : row) hash = hash * 31 + tree.findValue(point);
            }
        }
        sink = hash;
        return System.nanoTime() - start;
    }
    private static double median(long[] a) { var b=a.clone(); Arrays.sort(b);return b[b.length/2]; }
    private static void benchmark(boolean nativeMode, boolean scalar) throws Exception {
        var compilation = ManagementFactory.getCompilationMXBean();
        for (var entry : presets().entrySet()) for (String pattern : new String[]{"coherent","random","seeded"}) {
            var tree = list(entry.getValue()); var points = pattern.equals("seeded") ? seededCorpus(entry.getKey()) : corpus(entry.getValue(),pattern.equals("coherent"));
            int repeats=1, warmups=0, rejected=0;
            var warmSamples=new ArrayList<Double>();var warmJit=new ArrayList<Long>();
            double[] samples=new double[11],conservative=new double[11];long[] compiled=new long[11];
            boolean accepted=false;
            for(int attempt=0;attempt<8 && !accepted;attempt++) {
                long[] times=new long[10],jit=new long[10];
                long start=System.nanoTime();boolean stable=false;
                for (int warm=0;warm<300;warm++) {
                    long before=compilation.getTotalCompilationTime();
                    long ns=run(tree,points,repeats,nativeMode,scalar);warmups++;
                    long compilationMs=compilation.getTotalCompilationTime()-before;
                    warmSamples.add((double)ns/repeats/4096);warmJit.add(compilationMs);
                    if(ns<250_000_000L) {
                        repeats=Math.multiplyExact(repeats,Math.max(2,(int)Math.ceil(250_000_000.0/Math.max(1,ns))));
                        warm=-1;continue;
                    }
                    times[warm%10]=ns;jit[warm%10]=compilationMs;
                    if(warm>=9 && System.nanoTime()-start>=3_000_000_000L && Arrays.stream(jit).sum()==0) {
                        long[] recent=new long[5],older=new long[5];
                        for(int i=0;i<5;i++){recent[i]=times[(warm-i)%10];older[i]=times[(warm-i-5)%10];}
                        // GC cycles belong in the workload; compare consecutive
                        // window medians rather than selecting individually quiet rounds.
                        if(Math.abs(median(recent)-median(older))/median(older)<0.05){stable=true;break;}
                    }
                }
                if(!stable)throw new AssertionError("Warmup did not stabilize: "+entry.getKey()+"/"+pattern+" times="+warmSamples+" jit="+warmJit);
                accepted=true;
                for(int i=0;i<11;i++) {
                    long before=compilation.getTotalCompilationTime();
                    long ns=run(tree,points,repeats,nativeMode,scalar);
                    compiled[i]=compilation.getTotalCompilationTime()-before;
                    if(compiled[i]*1_000_000L>ns/100) {
                        System.out.println("BIOME_REJECT name="+entry.getKey()+"_"+pattern+" attempt="+attempt+" round="+i+" elapsed_ns="+ns+" jit_ms="+compiled[i]);
                        rejected++;accepted=false;break;
                    }
                    samples[i]=(double)ns/repeats/4096;
                    conservative[i]=(double)(ns-(compiled[i]+1)*1_000_000L)/repeats/4096;
                }
            }
            if(!accepted)throw new AssertionError("JIT remained active after eight measurement attempts");
            System.out.println("BIOME_BENCH name="+entry.getKey()+"_"+pattern+" samples="+Arrays.toString(samples)
                +" conservative="+Arrays.toString(conservative)+" jit_ms="+Arrays.toString(compiled)+" warmups="+warmups+" rejected_series="+rejected+" checksum="+sink);
            System.out.println("BIOME_WARMUP name="+entry.getKey()+"_"+pattern+" ns_per_query="+warmSamples+" jit_ms="+warmJit);
        }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        batchGuardSampler = Climate.empty();
        switch(args[0]) {
            case "parity" -> parity();
            case "bench-java" -> benchmark(false,false);
            case "bench-native" -> benchmark(true,false);
            case "scalar-native" -> benchmark(true,true);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
