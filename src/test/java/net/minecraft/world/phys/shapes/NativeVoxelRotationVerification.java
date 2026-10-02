package net.minecraft.world.phys.shapes;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.math.OctahedralGroup;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Complete unchanged Shapes.rotate caller, FFM/copies and all output consumption. */
public final class NativeVoxelRotationVerification {
    private static volatile long sink;
    private final boolean nativeMode;
    private final List<VoxelShape> inputs=new ArrayList<>();
    private final OctahedralGroup[] groups;
    private final Vec3 center;
    private NativeVoxelRotationVerification(boolean nativeMode,String name,String selectedGroup) {
        this.nativeMode=nativeMode;
        groups=selectedGroup.equals("all")?Arrays.stream(OctahedralGroup.values()).filter(g->g!=OctahedralGroup.IDENTITY).toArray(OctahedralGroup[]::new)
            :new OctahedralGroup[]{OctahedralGroup.valueOf(selectedGroup)};
        if(groups[0]==OctahedralGroup.IDENTITY)throw new AssertionError("Identity is not a native workload");
        center=name.startsWith("shifted")?new Vec3(-.125,.75,1.25):NativeVoxelRotationTest.CENTER;
        if(name.equals("registered_actual")) {
            for(var state:net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY) {
                var input=state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO);
                if(NativeVoxelRotationTest.eligible(input.shape))inputs.add(input);
            }
        } else {
            int n=Integer.parseInt(name.substring(name.lastIndexOf('_')+1));
            String pattern=name.substring(0,name.lastIndexOf('_'));
            for(int seed=0;seed<8;seed++) {
                var g=pattern.equals("odd")?NativeVoxelBoxesTest.grid(17,9,7,1977L+137L*seed,"random")
                    :pattern.equals("rows")?NativeVoxelBoxesTest.grid(8,1,64,1977L+137L*seed,"random")
                    :NativeVoxelBoxesTest.grid(n,n,n,1977L+137L*seed,
                        pattern.equals("cube")||pattern.equals("shifted")?"random":pattern);
                inputs.add(pattern.equals("cube")?new CubeVoxelShape(g):NativeVoxelBoxesTest.shape(g));
            }
        }
        if(inputs.isEmpty())throw new AssertionError("Empty workload");
        for(var input:inputs) {
            if(!NativeVoxelRotationTest.eligible(input.shape))throw new AssertionError("Native bypass");
            for(var group:groups)NativeVoxelRotationTest.same(JavaVoxelShapeRotation.rotate(input,group,center),Shapes.rotate(input,group,center));
        }
        System.out.println("ROTATION_WORKLOAD name="+name+" inputs="+inputs.size()+" groups="+groups.length+" selected="+selectedGroup);
    }
    private long run(int repeats) {
        long started=System.nanoTime(),sum=0;
        for(int r=0;r<repeats;r++)for(var input:inputs)for(var group:groups) {
            var result=nativeMode?Shapes.rotate(input,group,center):JavaVoxelShapeRotation.rotate(input,group,center);
            var g=(BitSetDiscreteVoxelShape)result.shape;
            long hash=31L*g.xSize+g.ySize;hash=hash*31+g.zSize;
            for(long word:g.storage.toLongArray())hash=hash*31+word;
            hash=hash*31+g.storage.size();hash=hash*31+g.xMin;hash=hash*31+g.yMin;hash=hash*31+g.zMin;
            hash=hash*31+g.xMax;hash=hash*31+g.yMax;hash=hash*31+g.zMax;
            for(var axis:Direction.Axis.values()) {
                var coords=result.getCoords(axis);
                for(int i=0;i<coords.size();i++)hash=hash*31+Double.doubleToRawLongBits(coords.getDouble(i));
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
                ns[round] = (double) elapsed / repeats / inputs.size() / groups.length;
            }
            if (accepted) {
                run(1);
                System.out.println(
                        "ROTATION_BENCH name="
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
        for(var pattern:List.of("empty","full","random","dense","sparse","checker","shell","slabs"))names.add(pattern+"_8");
        names.addAll(List.of("random_16","full_16","odd_17","rows_8","registered_actual","cube_8","shifted_8"));
        return names;
    }
    public static void main(String[] args) {
        NativeVoxelRotationTest.bootstrap();
        String selectedGroup=args.length>2?args[2]:"all";
        for(var name:names())if(args.length<2||args[1].equals("all")||args[1].equals(name))
            new NativeVoxelRotationVerification(args[0].equals("native"),name,selectedGroup).bench(name);
    }
}
