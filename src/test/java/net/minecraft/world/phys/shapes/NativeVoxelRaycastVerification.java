package net.minecraft.world.phys.shapes;

import java.lang.management.ManagementFactory;
import java.lang.foreign.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Equal outside rays through the complete public clip caller, with raw outputs consumed. */
public final class NativeVoxelRaycastVerification {
    private static volatile long sink;
    private final String mode;
    private record Input(VoxelShape shape, Vec3 start, Vec3 end, BlockPos position) {}
    private final List<Input> inputs = new ArrayList<>();

    private NativeVoxelRaycastVerification(String mode, String name) {
        this.mode = mode;
        var shapes = new ArrayList<VoxelShape>();
        if (name.equals("registered_actual") || name.equals("registered_diagonal")) {
            for (var state : Block.BLOCK_STATE_REGISTRY) {
                var s = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                var ray = NativeVoxelRaycastTest.rays().getFirst();
                if (!s.isEmpty() && NativeVoxelRaycast.clip(s, ray.start(), ray.end(), BlockPos.ZERO) != null) shapes.add(s);
            }
        } else if (name.startsWith("joined_registered_")) {
            int n = Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));
            var seen = new HashSet<String>();
            for (var pair : NativeVoxelJoinTest.registeredPairs(n)) {
                var s = Shapes.joinUnoptimized(pair.a(), pair.b(), BooleanOp.OR);
                String key = s.shape.xSize+":"+s.shape.ySize+":"+s.shape.zSize+Arrays.toString(((BitSetDiscreteVoxelShape)s.shape).storage.toLongArray());
                for (var axis : Direction.Axis.values()) key += Arrays.toString(s.getCoords(axis).toDoubleArray());
                var ray = NativeVoxelRaycastTest.rays().getFirst();
                if (seen.add(key) && !s.isEmpty() && NativeVoxelRaycast.clip(s,ray.start(),ray.end(),BlockPos.ZERO)!=null) shapes.add(s);
                if (shapes.size() == 32) break;
            }
        } else {
            int n = Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));
            String pattern = name.substring(0, name.lastIndexOf('_'));
            for (int seed = 0; seed < 8; seed++) {
                var g = pattern.equals("odd") ? NativeVoxelBoxesTest.grid(17,9,7,1977L+137L*seed,"random")
                    : pattern.equals("rows") ? NativeVoxelBoxesTest.grid(8,1,64,1977L+137L*seed,"random")
                    : pattern.equals("max") ? NativeVoxelBoxesTest.grid(256,1,256,1977L+137L*seed,"sparse")
                    : NativeVoxelBoxesTest.grid(n,n,n,1977L+137L*seed,
                        pattern.equals("cube") ? "full" : pattern.equals("single") || pattern.equals("cuboid") ? "empty"
                        : pattern.equals("translated") || pattern.equals("miss") || pattern.equals("holes") ? "random" : pattern);
                if (pattern.equals("holes")) g.storage.clear(g.getIndex(n/2,n/2,n/2));
                if (pattern.equals("single")) g.fill(n/2,n/2,n/2);
                if (pattern.equals("cuboid")) for(int x=1;x<n-1;x++) for(int y=0;y<n/2;y++) for(int z=0;z<n;z++) g.fill(x,y,z);
                shapes.add(pattern.equals("cube") ? new CubeVoxelShape(g) : NativeVoxelBoxesTest.shape(g));
            }
        }
        var position = name.startsWith("translated_") ? new BlockPos(30_000_000,-64,-30_000_000) : BlockPos.ZERO;
        for (var shape : shapes) {
            double[] low = new double[3], span = new double[3];
            for (var axis : Direction.Axis.values()) {
                var c = shape.getCoords(axis); int a = axis.ordinal();
                low[a] = c.getDouble(0); span[a] = c.getDouble(c.size()-1) - low[a];
                if (!(span[a]>0)) throw new AssertionError("Invalid benchmark coordinate extent");
            }
            var rays = name.equals("registered_diagonal") ? List.of(
                new NativeVoxelRaycastTest.Ray(new Vec3(-1,.17,.28),new Vec3(2,.83,.71)),
                new NativeVoxelRaycastTest.Ray(new Vec3(2,.83,.71),new Vec3(-1,.17,.28)),
                new NativeVoxelRaycastTest.Ray(new Vec3(.29,-1,.37),new Vec3(.61,2,.87)),
                new NativeVoxelRaycastTest.Ray(new Vec3(.61,2,.87),new Vec3(.29,-1,.37)),
                new NativeVoxelRaycastTest.Ray(new Vec3(.11,.37,-1),new Vec3(.83,.69,2)),
                new NativeVoxelRaycastTest.Ray(new Vec3(.83,.69,2),new Vec3(.11,.37,-1)),
                new NativeVoxelRaycastTest.Ray(new Vec3(-1,-.8,-.6),new Vec3(2,1.8,1.6)),
                new NativeVoxelRaycastTest.Ray(new Vec3(-1,2,1.1),new Vec3(2,2.1,1.2)))
                : NativeVoxelRaycastTest.rays().subList(0,8);
            for (int i=0;i<rays.size();i++) {
                var r = rays.get(i);
                if (name.startsWith("holes_")) {
                    int n=shape.shape.xSize;
                    double[] center=new double[3];
                    for(var axis:Direction.Axis.values()) {
                        var c=shape.getCoords(axis);int a=axis.ordinal();
                        double middle=(c.getDouble(n/2)+c.getDouble(n/2+1))*.5;
                        center[a]=(middle-low[a])/span[a];
                    }
                    r=new NativeVoxelRaycastTest.Ray(new Vec3(center[0],center[1],center[2]),r.end());
                }
                if (name.startsWith("miss_")) r = new NativeVoxelRaycastTest.Ray(new Vec3(-1,2+i*.125,2),new Vec3(2,2.5+i*.125,2.5));
                var start = new Vec3(low[0]+r.start().x*span[0]+position.getX(),low[1]+r.start().y*span[1]+position.getY(),low[2]+r.start().z*span[2]+position.getZ());
                var end = new Vec3(low[0]+r.end().x*span[0]+position.getX(),low[1]+r.end().y*span[1]+position.getY(),low[2]+r.end().z*span[2]+position.getZ());
                var expected = JavaVoxelRaycast.clip(shape,start,end,position);
                if (expected != null && expected.isInside()) throw new AssertionError("Immediate inside hit in outside workload");
                var nativeHit = NativeVoxelRaycast.clip(shape,start,end,position);
                if (nativeHit == null) throw new AssertionError("Native bypass: " + name);
                long[] raw = NativeVoxelRaycastTest.raw(expected);
                if (!Arrays.equals(raw,NativeVoxelRaycastTest.raw(shape.clip(start,end,position)))
                    || !Arrays.equals(raw,NativeVoxelRaycastTest.raw(nativeHit.orElse(null)))
                    || !Arrays.equals(raw,NativeVoxelRaycastTest.raw(JavaVoxelRaycast.vanilla(shape,start,end,position))))
                    throw new AssertionError("Parity: " + name);
                inputs.add(new Input(shape,start,end,position));
            }
        }
        if (inputs.isEmpty()) throw new AssertionError("Empty workload: " + name);
        System.out.println("RAY_WORKLOAD name="+name+" shapes="+shapes.size()+" queries="+inputs.size()+" mode="+mode);
    }

    private long run(int repeats) {
        long started=System.nanoTime(),sum=0;
        for (int r=0;r<repeats;r++) for (var input:inputs) {
            var result=switch(mode) {
                case "native" -> input.shape.clip(input.start,input.end,input.position);
                case "java" -> JavaVoxelRaycast.clip(input.shape,input.start,input.end,input.position);
                case "vanilla" -> JavaVoxelRaycast.vanilla(input.shape,input.start,input.end,input.position);
                default -> throw new AssertionError(mode);
            };
            long hash=result==null?0:1;
            if (result!=null) {
                var p=result.getLocation();var b=result.getBlockPos();
                hash=31*hash+Double.doubleToRawLongBits(p.x);hash=31*hash+Double.doubleToRawLongBits(p.y);
                hash=31*hash+Double.doubleToRawLongBits(p.z);hash=31*hash+result.getDirection().ordinal();
                hash=31*hash+(result.isInside()?1:0);hash=31*hash+result.getType().ordinal();
                hash=31*hash+(result.isWorldBorderHit()?1:0);hash=31*hash+b.getX();hash=31*hash+b.getY();hash=31*hash+b.getZ();
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
            long initialCollections = collections();
            long[] recent = new long[10], compilation = new long[10];
            boolean stable = false;
            for (int round = 0; round < 300; round++) {
                long before = jit.getTotalCompilationTime(), elapsed = run(repeats);
                if (elapsed < 500_000_000) {
                    repeats *= Math.max(2, (int) Math.ceil(500_000_000.0 / Math.max(elapsed, 1)));
                    round = -1;
                    continue;
                }
                recent[round % 10] = elapsed;
                compilation[round % 10] = jit.getTotalCompilationTime() - before;
                if (round >= 9
                        && System.nanoTime() - started > Long.getLong("mattmc.raycast.warmupNanos", 15_000_000_000L)
                        && collections() - initialCollections >= 2
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
            long warmupNanos = System.nanoTime() - started;
            long warmupCollections = collections() - initialCollections;
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
                        "RAY_BENCH name="
                                + name
                                + " warmup_ns=" + warmupNanos + " warmup_gc=" + warmupCollections
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
    private static long collections() {
        long count = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans())
            count += Math.max(0, gc.getCollectionCount());
        return count;
    }

    public static List<String> names() {
        return List.of("full_8","cube_8","random_8","random_16","dense_16","sparse_8","checker_16",
            "shell_16","odd_17","rows_8","max_256","registered_actual","registered_diagonal","joined_registered_8","joined_registered_16",
            "single_8","single_16","cuboid_8","cuboid_16","translated_16","miss_16","holes_8","holes_16");
    }
    /** Pin the mutator separately from GC/compiler workers, equally in every mode. */
    private static void pinThreads() throws Throwable {
        int cpu=Integer.getInteger("mattmc.raycast.cpu",-1);
        if(cpu<0)return;
        String background=System.getProperty("mattmc.raycast.backgroundCpus");
        var libc=SymbolLookup.libraryLookup("libc.so.6",Arena.global());
        var linker=Linker.nativeLinker();
        var tidCall=linker.downcallHandle(libc.find("gettid").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT));
        var affinity=linker.downcallHandle(libc.find("sched_setaffinity").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
        int current=(int)tidCall.invokeExact();int workers=0;
        try(var arena=Arena.ofConfined()) {
            var mask=arena.allocate(128,8);
            for(var value:background.split(",")) {
                int c=Integer.parseInt(value);long at=c/8;
                mask.set(ValueLayout.JAVA_BYTE,at,(byte)(mask.get(ValueLayout.JAVA_BYTE,at)|(1<<(c%8))));
            }
            try(var tasks=Files.list(Path.of("/proc/self/task"))) {
                for(var task:tasks.toList()) {
                    int tid=Integer.parseInt(task.getFileName().toString());
                    if(tid==current)continue;
                    int status=(int)affinity.invokeExact(tid,128L,mask);
                    if(status!=0 && Files.exists(task))throw new IllegalStateException("Cannot pin JVM worker "+tid);
                    workers++;
                }
            }
            mask.fill((byte)0);mask.set(ValueLayout.JAVA_BYTE,cpu/8,(byte)(1<<(cpu%8)));
            if((int)affinity.invokeExact(0,128L,mask)!=0)throw new IllegalStateException("Cannot pin benchmark mutator");
        }
        System.out.println("RAY_AFFINITY main="+cpu+" background="+background+" workers="+workers);
    }

    public static void main(String[] args) throws Throwable {
        pinThreads();
        NativeVoxelBoxesTest.bootstrap();
        var selected=args.length<2?Set.of("all"):new HashSet<>(Arrays.asList(args[1].split(",")));
        for (var name:names()) if (selected.contains("all")||selected.contains(name)) {
            pinThreads();
            new NativeVoxelRaycastVerification(args[0],name).bench(name);
        }
    }
}
