package net.minecraft.world.phys.shapes;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Full toAabbs callers, allocation/clone/transfers/callbacks and every result coordinate. */
public final class NativeVoxelBoxesVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final List<VoxelShape> inputs = new ArrayList<>();
    private NativeVoxelBoxesVerification(boolean nativeMode, String name) {
        this.nativeMode = nativeMode;
        int n = name.equals("registered_actual") ? 0 : Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));
        if (name.equals("registered_actual")) {
            for (var state : net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY) {
                var input = state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
                if (eligible(input.shape)) inputs.add(input);
            }
        } else if (name.startsWith("joined_registered_")) {
            var seen = new HashSet<String>();
            for (var pair : NativeVoxelJoinTest.registeredPairs(n)) {
                var input = Shapes.joinUnoptimized(pair.a(), pair.b(), BooleanOp.OR);
                var g = (BitSetDiscreteVoxelShape)input.shape;
                String key = g.xSize + ":" + g.ySize + ":" + g.zSize + Arrays.toString(g.storage.toLongArray());
                for (var axis : Direction.Axis.values()) key += Arrays.toString(input.getCoords(axis).toDoubleArray());
                if (seen.add(key) && eligible(g)) inputs.add(input);
                if (inputs.size() == 32) break;
            }
        } else {
            String pattern = name.substring(0, name.lastIndexOf('_'));
            for (int seed = 0; seed < 8; seed++) {
                var g = pattern.equals("odd") ? NativeVoxelBoxesTest.grid(17,9,7,1977L+137L*seed,"random")
                    : pattern.equals("rows") ? NativeVoxelBoxesTest.grid(8,1,64,1977L+137L*seed,"random")
                    : NativeVoxelBoxesTest.grid(n,n,n,1977L+137L*seed,pattern);
                inputs.add(NativeVoxelBoxesTest.shape(g));
            }
        }
        if (inputs.isEmpty()) throw new AssertionError("Empty workload: " + name);
        for (var input : inputs) {
            if (input.getClass()!=ArrayVoxelShape.class && input.getClass()!=CubeVoxelShape.class)
                throw new AssertionError("Public list compatibility fallback: " + name);
            if (!eligible(input.shape)) throw new AssertionError("Native bypass: " + name);
            if (!JavaVoxelBoxList.toAabbs(input).equals(input.toAabbs())) throw new AssertionError("Parity: " + name);
        }
        System.out.println("BOX_WORKLOAD name=" + name + " inputs=" + inputs.size());
    }
    // Validate the exact production guard without introducing a benchmark-only
    // no-op consumer profile at the native callback site. Both timed callers
    // receive only the actual public AABB-list consumer.
    private static boolean eligible(DiscreteVoxelShape grid) {
        long cells=(long)grid.xSize*grid.ySize*grid.zSize;
        return grid instanceof BitSetDiscreteVoxelShape bitset && grid.xSize>0 && grid.ySize>0 && grid.zSize>0
            && grid.xSize<=256 && grid.ySize<=256 && grid.zSize<=256 && cells>=512 && cells<=65536
            && bitset.storage.length()<=65536;
    }
    private long run(int repeats) {
        long started=System.nanoTime(),sum=0;
        for (int r=0;r<repeats;r++) for (var input:inputs) {
            List<AABB> result=nativeMode?input.toAabbs():JavaVoxelBoxList.toAabbs(input);
            long hash=result.size();
            for(var box:result) {
                hash=hash*31+Double.doubleToRawLongBits(box.minX); hash=hash*31+Double.doubleToRawLongBits(box.minY);
                hash=hash*31+Double.doubleToRawLongBits(box.minZ); hash=hash*31+Double.doubleToRawLongBits(box.maxX);
                hash=hash*31+Double.doubleToRawLongBits(box.maxY); hash=hash*31+Double.doubleToRawLongBits(box.maxZ);
            }
            sum+=hash;
        }
        sink=sum;return System.nanoTime()-started;
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
                        "BOX_BENCH name="
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
        for(var pattern:List.of("empty","full","random","dense","sparse","checker","shell","slabs"))
            for(int n:new int[]{8,16})names.add(pattern+"_"+n);
        names.add("registered_actual");names.add("odd_17");names.add("rows_8");names.add("joined_registered_8");names.add("joined_registered_16");
        return names;
    }
    public static void main(String[] args) {
        NativeVoxelBoxesTest.bootstrap();
        for(var name:names())if(args.length<2||args[1].equals("all")||args[1].equals(name))
            new NativeVoxelBoxesVerification(args[0].equals("native"),name).bench(name);
    }
}
