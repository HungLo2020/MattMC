package net.minecraft.world.phys.shapes;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Equivalent complete public queries, freshly recomputed and raw output consumed. */
public final class NativeVoxelClosestPointVerification {
    private static volatile long sink;
    private final String mode;
    private record Input(VoxelShape shape, Vec3 point) {}
    private final List<Input> inputs = new ArrayList<>();
    private NativeVoxelClosestPointVerification(String mode, String name) {
        this.mode=mode;
        var shapes=new ArrayList<VoxelShape>();
        if(name.equals("registered_actual")) {
            for(var state:Block.BLOCK_STATE_REGISTRY) {
                var s=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
                if(!s.isEmpty() && NativeVoxelClosestPoint.find(s,new Vec3(.5,.5,.5))!=null)shapes.add(s);
            }
        } else if(name.startsWith("joined_registered_")) {
            int n=Integer.parseInt(name.substring(name.lastIndexOf('_')+1));
            var seen=new HashSet<String>();
            for(var pair:NativeVoxelJoinTest.registeredPairs(n)) {
                var s=Shapes.joinUnoptimized(pair.a(),pair.b(),BooleanOp.OR);
                String key=s.shape.xSize+":"+s.shape.ySize+":"+s.shape.zSize+Arrays.toString(((BitSetDiscreteVoxelShape)s.shape).storage.toLongArray());
                for(var axis:Direction.Axis.values())key+=Arrays.toString(s.getCoords(axis).toDoubleArray());
                if(seen.add(key)&&!s.isEmpty()&&NativeVoxelClosestPoint.find(s,new Vec3(.5,.5,.5))!=null)shapes.add(s);
                if(shapes.size()==32)break;
            }
        } else {
            int n=Integer.parseInt(name.substring(name.lastIndexOf('_')+1));
            String pattern=name.substring(0,name.lastIndexOf('_'));
            for(int seed=0;seed<8;seed++) {
                var g=pattern.equals("odd")?NativeVoxelBoxesTest.grid(17,9,7,1977L+137L*seed,"random")
                    :pattern.equals("rows")?NativeVoxelBoxesTest.grid(8,1,64,1977L+137L*seed,"random")
                    :pattern.equals("max")?NativeVoxelBoxesTest.grid(256,1,256,1977L+137L*seed,"sparse")
                    :NativeVoxelBoxesTest.grid(n,n,n,1977L+137L*seed,pattern.equals("cube")?"full":pattern.equals("single")||pattern.equals("cuboid")?"empty":pattern);
                if(pattern.equals("single"))g.fill(n/2,n/2,n/2);
                if(pattern.equals("cuboid"))for(int x=1;x<n-1;x++)for(int y=0;y<n/2;y++)for(int z=0;z<n;z++)g.fill(x,y,z);
                shapes.add(pattern.equals("cube")?new CubeVoxelShape(g):NativeVoxelBoxesTest.shape(g));
            }
        }
        for(var s:shapes)for(var q:NativeVoxelClosestPointTest.probes()) {
            var migrated=NativeVoxelClosestPoint.find(s,q);
            if(migrated==null)throw new AssertionError("Native bypass: "+name);
            long[] expected=NativeVoxelClosestPointTest.raw(JavaVoxelClosestPoint.closestPointTo(s,q));
            if(!Arrays.equals(expected,NativeVoxelClosestPointTest.raw(migrated))
                || !Arrays.equals(expected,NativeVoxelClosestPointTest.raw(JavaVoxelClosestPoint.vanilla(s,q))))throw new AssertionError("Parity: "+name);
            inputs.add(new Input(s,q));
        }
        if(inputs.isEmpty())throw new AssertionError("Empty workload "+name);
        System.out.println("CLOSEST_WORKLOAD name="+name+" shapes="+shapes.size()+" queries="+inputs.size()+" mode="+mode);
    }
    private long run(int repeats) {
        long started=System.nanoTime(),sum=0;
        for(int r=0;r<repeats;r++)for(var input:inputs) {
            var result=switch(mode) {
                case "native"->input.shape.closestPointTo(input.point);
                case "java"->JavaVoxelClosestPoint.closestPointTo(input.shape,input.point);
                case "vanilla"->JavaVoxelClosestPoint.vanilla(input.shape,input.point);
                default->throw new AssertionError(mode);
            };
            long hash=result.isPresent()?1:0;
            if(result.isPresent()) {
                var p=result.get();hash=31*hash+Double.doubleToRawLongBits(p.x);
                hash=31*hash+Double.doubleToRawLongBits(p.y);hash=31*hash+Double.doubleToRawLongBits(p.z);
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
                        && System.nanoTime() - started > Long.getLong("mattmc.closest.warmupNanos", 6_000_000_000L)
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
                        "CLOSEST_BENCH name="
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
        return List.of("full_8","cube_8","random_8","random_16","dense_16","sparse_8","checker_16",
            "shell_16","odd_17","rows_8","max_256","registered_actual","joined_registered_8","joined_registered_16","single_8","single_16","cuboid_8","cuboid_16");
    }
    public static void main(String[] args) {
        NativeVoxelBoxesTest.bootstrap();
        for(var name:names())if(args.length<2||args[1].equals("all")||args[1].equals(name))
            new NativeVoxelClosestPointVerification(args[0],name).bench(name);
    }
}
