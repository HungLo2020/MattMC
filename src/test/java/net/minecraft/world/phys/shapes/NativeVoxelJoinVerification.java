package net.minecraft.world.phys.shapes;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.core.Direction;

/** Complete original/public native join callers; fresh results, transfers, allocations and full consumption. */
public final class NativeVoxelJoinVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final List<NativeVoxelJoinTest.Pair> inputs=new ArrayList<>();
    private final BooleanOp op;
    private NativeVoxelJoinVerification(boolean nativeMode,String name) {
        this.nativeMode=nativeMode;
        op=name.contains("_and_")?BooleanOp.AND:name.contains("_xor_")?BooleanOp.NOT_SAME:name.contains("_first_")?BooleanOp.ONLY_FIRST:BooleanOp.OR;
        int n=Integer.parseInt(name.substring(name.lastIndexOf('_')+1));
        if(name.startsWith("registered_")) {
            var all=NativeVoxelJoinTest.registeredPairs(n);
            var geometries=new HashSet<String>();
            for(var pair:all) {
                var key=new StringBuilder();var grid=(BitSetDiscreteVoxelShape)pair.a().shape;
                key.append(grid.getXSize()).append(',').append(grid.getYSize()).append(',').append(grid.getZSize());
                key.append(Arrays.toString(grid.storage.toLongArray()));
                for(var axis:Direction.Axis.values()){var coords=pair.a().getCoords(axis);
                    for(int i=0;i<coords.size();i++)key.append(':').append(Double.doubleToRawLongBits(coords.getDouble(i)));}
                if(!geometries.add(key.toString()))continue;
                var maps=NativeVoxelJoinTest.mergers(pair,op);
                if(NativeVoxelJoin.join(pair.a().shape,pair.b().shape,maps[0],maps[1],maps[2],op)!=null)inputs.add(pair);
                if(inputs.size()==32)break;
            }
        } else {
            String kind=name.substring(0,name.indexOf('_'));
            String pattern=name.contains("_full_")?"full":name.contains("_sparse_")?"sparse":name.contains("_shell_")?"shell":"random";
            for(int seed=0;seed<8;seed++)inputs.add(NativeVoxelJoinTest.fixture(kind,n,1977L+137L*seed,pattern));
        }
        if(inputs.isEmpty())throw new AssertionError("Empty native workload: "+name);
        for(var pair:inputs)NativeVoxelJoinTest.compare(pair,op,true);
        System.out.println("VOXEL_WORKLOAD name="+name+" pairs="+inputs.size());
    }
    private long run(int repeats) {
        long start=System.nanoTime(),sum=0;
        for(int r=0;r<repeats;r++)for(var pair:inputs) {
            var output=nativeMode?Shapes.joinUnoptimized(pair.a(),pair.b(),op):JavaShapeJoin.joinUnoptimized(pair.a(),pair.b(),op);
            var bits=(BitSetDiscreteVoxelShape)output.shape;
            long hash=bits.getXSize()*31L+bits.getYSize()*17L+bits.getZSize();
            for(long word:bits.storage.toLongArray())hash=hash*31+word;
            for(var axis:Direction.Axis.values()){
                hash=hash*31+bits.firstFull(axis);hash=hash*31+bits.lastFull(axis);
                var coords=output.getCoords(axis);for(int i=0;i<coords.size();i++)hash=hash*31+Double.doubleToRawLongBits(coords.getDouble(i));
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
                        "VOXEL_BENCH name="
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
        for(String kind:List.of("identical","cube","indirect","disjoint","anisotropic"))
            for(int n:new int[]{8,16})names.add(kind+"_or_random_"+n);
        for(String op:List.of("and","xor","first"))for(int n:new int[]{8,16})names.add("identical_"+op+"_random_"+n);
        for(String pattern:List.of("full","sparse","shell"))for(int n:new int[]{8,16})names.add("identical_or_"+pattern+"_"+n);
        names.add("registered_or_8");names.add("registered_or_16");
        return names;
    }
    public static void main(String[] args) {
        NativeVoxelJoinTest.bootstrap();
        for(var name:names())if(args.length<2||args[1].equals("all")||args[1].equals(name))
            new NativeVoxelJoinVerification(args[0].equals("native"),name).bench(name);
    }
}
