package net.minecraft.world.phys.shapes;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVoxelRaycastTest {
    record Ray(Vec3 start, Vec3 end) {}
    @BeforeAll static void bootstrap() { NativeVoxelBoxesTest.bootstrap(); }

    static long[] raw(BlockHitResult hit) {
        if (hit == null) return new long[0];
        var p = hit.getLocation(); var b = hit.getBlockPos();
        return new long[]{Double.doubleToRawLongBits(p.x), Double.doubleToRawLongBits(p.y), Double.doubleToRawLongBits(p.z),
            hit.getDirection().ordinal(), hit.isInside() ? 1 : 0, hit.getType().ordinal(), hit.isWorldBorderHit() ? 1 : 0,
            b.getX(), b.getY(), b.getZ()};
    }

    static List<Ray> rays() {
        return List.of(
            new Ray(new Vec3(-1, .3, .6), new Vec3(2, .4, .7)),
            new Ray(new Vec3(2, .8, .9), new Vec3(-1, .1, .2)),
            new Ray(new Vec3(.5, -1, .5), new Vec3(.5, 2, .5)),
            new Ray(new Vec3(.5, 2, .5), new Vec3(.5, -1, .5)),
            new Ray(new Vec3(.3, .6, -1), new Vec3(.4, .7, 2)),
            new Ray(new Vec3(.8, .9, 2), new Vec3(.1, .2, -1)),
            new Ray(new Vec3(-1, -1, -1), new Vec3(2, 2, 2)),
            new Ray(new Vec3(-1, 2, .5), new Vec3(2, 2, .5)),
            new Ray(new Vec3(-1, 1, .5), new Vec3(0, 1, .5)),
            new Ray(new Vec3(.5, .5, .5), new Vec3(2, .5, .5)),
            new Ray(new Vec3(-0., 0, 0), new Vec3(1, 1, 1)),
            new Ray(new Vec3(.5, .5, .5), new Vec3(.500001, .5, .5)));
    }

    static Ray translate(Ray ray, BlockPos p) {
        return new Ray(ray.start.add(p.getX(), p.getY(), p.getZ()), ray.end.add(p.getX(), p.getY(), p.getZ()));
    }

    static void compare(VoxelShape s, Ray r, BlockPos p, boolean nativeRequired) {
        var expected = JavaVoxelRaycast.clip(s, r.start, r.end, p);
        // The unchanged Java inside precheck may combine several NaN operands.
        // Java does not specify which NaN sign/payload that arithmetic returns.
        // Only this non-migrated case compares canonical NaNs. Every finite
        // public result and every native outside result still compares raw bits.
        boolean javaNaN = expected != null && expected.isInside()
            && (!finite(r.start) || !finite(r.end));
        if (s.getClass() == ArrayVoxelShape.class || s.getClass() == CubeVoxelShape.class)
            assertArrayEquals(compatibleRaw(JavaVoxelRaycast.vanilla(s, r.start, r.end, p), javaNaN),
                compatibleRaw(expected, javaNaN), "original box stack");
        var actual = s.clip(r.start, r.end, p);
        assertArrayEquals(compatibleRaw(expected, javaNaN), compatibleRaw(actual, javaNaN), "public clip");
        if (actual != null) assertSame(p, actual.getBlockPos(), "block position ownership");
        var outside = NativeVoxelRaycast.clip(s, r.start, r.end, p);
        if (nativeRequired) assertNotNull(outside, "outside native eligibility");
        if (outside != null) {
            var boxHit = AABB.clip(s.toAabbs(), r.start, r.end, p);
            assertArrayEquals(raw(boxHit), raw(outside.orElse(null)), "ordered outside intersection");
        }
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static long[] compatibleRaw(BlockHitResult hit, boolean javaNaN) {
        long[] values = raw(hit);
        if (javaNaN && hit != null) for (int i = 0; i < 3; i++) {
            if (Double.isNaN(Double.longBitsToDouble(values[i]))) values[i] = Double.doubleToLongBits(Double.NaN);
        }
        return values;
    }

    @Test void exhaustiveSmallGridsAndComplexPatterns() {
        for (int mask = 0; mask < 4096; mask++) {
            var g = new BitSetDiscreteVoxelShape(2, 2, 3);
            for (int i = 0; i < 12; i++) if ((mask & (1 << i)) != 0) g.fill(i / 6, (i / 3) % 2, i % 3);
            var s = NativeVoxelBoxesTest.shape(g);
            for (var ray : rays().subList(0, 4)) compare(s, ray, BlockPos.ZERO, false);
        }
        int fixtures = 0;
        for (int[] d : new int[][]{{8,8,8},{16,16,16},{17,9,7},{3,3,65},{8,1,64},{1,256,256},{256,1,256},{32,8,16}})
            for (String pattern : List.of("empty","full","random","sparse","dense","checker","shell","slabs"))
                for (int seed = 0; seed < 3; seed++) {
                    var g = NativeVoxelBoxesTest.grid(d[0], d[1], d[2], 1977L + 137L * seed, pattern);
                    var before = g.storage.toLongArray();
                    for (VoxelShape s : List.of(NativeVoxelBoxesTest.shape(g), new CubeVoxelShape(g)))
                        for (var r : rays()) compare(s, r, BlockPos.ZERO, true);
                    assertArrayEquals(before, g.storage.toLongArray()); fixtures++;
                }
        System.out.println("RAY_COMPLEX_PARITY exhaustive=4096 complex_fixtures=" + fixtures);
    }

    @Test void wholeGridMissProofMatchesOriginalAtToleranceAndRoundedEndpoints() {
        var grid = NativeVoxelBoxesTest.grid(8,8,8,1977,"random");
        grid.storage.clear(grid.getIndex(4,4,4));
        double[] coordinates = new double[9];
        for (int i=0;i<=8;i++) coordinates[i]=i/8.0;
        var shape = new ArrayVoxelShape(grid, DoubleArrayList.wrap(coordinates.clone()),
            DoubleArrayList.wrap(coordinates.clone()), DoubleArrayList.wrap(coordinates.clone()));
        int cases=0;
        for (var position : List.of(BlockPos.ZERO, new BlockPos(30_000_000,-64,-30_000_000),
            new BlockPos(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE))) {
            for (double height : new double[]{-1e-7,Math.nextDown(-1e-7),Math.nextUp(-1e-7),
                -0.0,0.0,1.0,1+1e-7,Math.nextDown(1+1e-7),Math.nextUp(1+1e-7)}) {
                for (double delta : new double[]{0.0,-0.0,1e-7,-1e-7,Math.nextUp(1e-7),Math.nextDown(1e-7)}) {
                    var ray = new Ray(new Vec3(-1,height,.5),new Vec3(2,height+delta,.5));
                    compare(shape,translate(ray,position),position,true);cases++;
                }
            }
            for (var ray : List.of(new Ray(new Vec3(1e16,2,.5),new Vec3(1,2,.5)),
                new Ray(new Vec3(.5625,.5625,.5625),new Vec3(2,.5625,.5625)),
                new Ray(new Vec3(2,.5,.5),new Vec3(-1,.5,.5)))) {
                compare(shape,translate(ray,position),position,true);cases++;
            }
        }
        System.out.println("RAY_MISS_PROOF_PARITY cases="+cases);
    }

    @Test void everyRegisteredShapeAndDerivedJoins() {
        int states = 0, nativeStates = 0, joins = 0;
        var shell = NativeVoxelBoxesTest.shape(NativeVoxelBoxesTest.grid(8,8,8,71,"shell"));
        for (var state : Block.BLOCK_STATE_REGISTRY) {
            var s = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            for (var ray : rays().subList(0,2)) compare(s, ray, BlockPos.ZERO, false);
            states++;
            if (NativeVoxelRaycast.clip(s, rays().getFirst().start, rays().getFirst().end, BlockPos.ZERO) != null) nativeStates++;
            if (states % 47 == 0 && !s.isEmpty()) {
                var joined = Shapes.joinUnoptimized(s, shell, BooleanOp.OR);
                for (var ray : rays()) compare(joined, ray, BlockPos.ZERO, false);
                joins++;
            }
        }
        assertTrue(nativeStates > 0);
        System.out.println("RAY_REGISTERED_PARITY states="+states+" native_states="+nativeStates+" joins="+joins);
    }

    @Test void exactTolerancesTiesSignedZerosReversedAndMixedCoordinates() {
        var full = NativeVoxelBoxesTest.grid(8,8,8,71,"full");
        var s = new CubeVoxelShape(full);
        for (var r : rays()) compare(s, r, BlockPos.ZERO, true);
        assertEquals(Direction.WEST, s.clip(new Vec3(-1,-1,-1), new Vec3(2,2,2), BlockPos.ZERO).getDirection());
        assertNull(s.clip(new Vec3(-1,.5,.5), new Vec3(0,.5,.5), BlockPos.ZERO));
        for (double edge : new double[]{1-1e-7, Math.nextDown(1+1e-7), 1+1e-7, Math.nextUp(1+1e-7)})
            compare(s, new Ray(new Vec3(-1,edge,.5), new Vec3(2,edge,.5)), BlockPos.ZERO, true);
        var g = NativeVoxelBoxesTest.grid(8,9,8,71,"checker");
        for (int mask = 0; mask < 8; mask++) {
            var shape = new ArrayVoxelShape(g, (mask&1)!=0?new CubePointRange(8):NativeVoxelJoinTest.coords(8,0),
                (mask&2)!=0?new CubePointRange(9):NativeVoxelJoinTest.coords(9,0),
                (mask&4)!=0?new CubePointRange(8):NativeVoxelJoinTest.coords(8,0));
            for (var r : rays()) compare(shape, r, BlockPos.ZERO, true);
        }
        double[] repeated = {-0., 0., .25, .5, .5, .75, 1., 1., 1.};
        double[] reversed = {3,2,1,0,-1,-2,-3,-4,-5};
        var odd = new ArrayVoxelShape(NativeVoxelBoxesTest.grid(8,8,8,71,"checker"), reversed,repeated,reversed);
        for (var r : rays()) compare(odd, r, BlockPos.ZERO, true);
    }

    @Test void translationsPrecisionLimitsAndRandomRays() {
        var g = NativeVoxelBoxesTest.grid(16,16,16,1977,"random");
        var s = NativeVoxelBoxesTest.shape(g);
        for (var pos : List.of(BlockPos.ZERO, new BlockPos(31,-64,-71),new BlockPos(30_000_000,319,-30_000_000),
            new BlockPos(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE),new BlockPos.MutableBlockPos(41,75,-33)))
            for (var ray : rays()) compare(s, translate(ray,pos), pos, true);
        var random = new Random(1977);
        for (int i = 0; i < 2048; i++) {
            Vec3 start = new Vec3(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3);
            Vec3 end = new Vec3(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3);
            compare(s,new Ray(start,end),BlockPos.ZERO,true);
        }
        for (double scale : new double[]{1e-170,1e-8,1e150}) {
            double[] c = new double[9]; for(int i=0;i<9;i++) c[i]=(i-4)*scale;
            var shape = new ArrayVoxelShape(NativeVoxelBoxesTest.grid(8,8,8,71,"random"),c,c,c);
            compare(shape,new Ray(new Vec3(-10*scale,.3*scale,.7*scale),new Vec3(10*scale,-.3*scale,-.7*scale)),BlockPos.ZERO,true);
        }
    }

    @Test void exactCuboidProofStaleBoundsPaddingAndChangingInputs() {
        for (int[] d : new int[][]{{8,9,8},{17,9,7},{3,3,65}}) for (boolean fullZ : new boolean[]{false,true}) {
            var g = new BitSetDiscreteVoxelShape(d[0],d[1],d[2]);
            int z0=fullZ?0:1,z1=fullZ?d[2]:d[2]-1;
            for(int x=1;x<d[0];x++)for(int y=1;y<d[1]-1;y++)for(int z=z0;z<z1;z++)g.fill(x,y,z);
            var s=NativeVoxelBoxesTest.shape(g);
            for(var r:rays())compare(s,r,BlockPos.ZERO,true);
            g.storage.clear(g.getIndex(1,1,z0));g.storage.set(0); // Same population, invalid hints.
            for(var r:rays())compare(s,r,BlockPos.ZERO,true);
        }
        var g=NativeVoxelBoxesTest.grid(17,9,7,71,"random");g.xMin=-77;g.zMax=Integer.MAX_VALUE;g.storage.set(1071,1100);
        var s=NativeVoxelBoxesTest.shape(g);
        for(var r:rays())compare(s,r,BlockPos.ZERO,true);
        for(int i=0;i<20;i++){g.storage.flip(0,1071);compare(s,rays().getFirst(),BlockPos.ZERO,true);}
        g.storage.set(65537); assertNull(NativeVoxelRaycast.clip(s,rays().getFirst().start,rays().getFirst().end,BlockPos.ZERO));
        compare(s,rays().getFirst(),BlockPos.ZERO,false);
        var c=DoubleArrayList.wrap(new double[]{0,.125,.25,.375,.5,.625,.75,.875,1});
        var mutable=new ArrayVoxelShape(NativeVoxelBoxesTest.grid(8,8,8,71,"random"),c,c,c);
        for(int i=0;i<20;i++){c.set(4,.4+i*.005);compare(mutable,rays().getFirst(),BlockPos.ZERO,true);}
        var padding=new BitSetDiscreteVoxelShape(8,8,8);padding.storage.set(513);
        compare(new CubeVoxelShape(padding),rays().getFirst(),BlockPos.ZERO,true);
    }

    @Test void nonfiniteQueriesAndCoordinatesRetainJavaBehavior() {
        var g=NativeVoxelBoxesTest.grid(8,8,8,71,"checker");
        for(double value:new double[]{Double.NaN,Double.longBitsToDouble(0x7ff8000000000001L),
            Double.longBitsToDouble(0xfff8000000000002L),Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            double[] c={0,.125,.25,.375,.5,.625,.75,.875,1};c[4]=value;
            var s=new ArrayVoxelShape(g,c,c,c);
            for(var r:rays())compare(s,r,BlockPos.ZERO,false);
            compare(new CubeVoxelShape(g),new Ray(new Vec3(value,.3,.6),new Vec3(2,.4,.7)),BlockPos.ZERO,false);
            var full=NativeVoxelBoxesTest.grid(8,8,8,71,"full");
            compare(new ArrayVoxelShape(full,c,c,c),rays().getFirst(),BlockPos.ZERO,false);
        }
        compare(new CubeVoxelShape(g),new Ray(new Vec3(Double.MAX_VALUE,.5,.5),new Vec3(-Double.MAX_VALUE,.5,.5)),BlockPos.ZERO,false);
    }

    @Test void customOverridesCoordinateReadsExceptionsAndNulls() {
        var events=new ArrayList<String>();var g=NativeVoxelBoxesTest.grid(8,8,8,71,"checker");var r=rays().getFirst();
        var c=new AbstractDoubleList(){public int size(){events.add("size");return 9;}
            public double getDouble(int i){events.add("get:"+i);return i/8.;}};
        var s=new ArrayVoxelShape(g,c,c,c);events.clear();
        var expected=JavaVoxelRaycast.clip(s,r.start,r.end,BlockPos.ZERO);var trace=List.copyOf(events);events.clear();
        assertArrayEquals(raw(expected),raw(s.clip(r.start,r.end,BlockPos.ZERO)));assertEquals(trace,events);
        var custom=new VoxelShape(g){public it.unimi.dsi.fastutil.doubles.DoubleList getCoords(Direction.Axis a){return new CubePointRange(8);}
            @Override public List<AABB> toAabbs(){events.add("boxes");return List.of(new AABB(0,0,0,1,1,1));}};
        events.clear();expected=JavaVoxelRaycast.clip(custom,r.start,r.end,BlockPos.ZERO);trace=List.copyOf(events);events.clear();
        assertArrayEquals(raw(expected),raw(custom.clip(r.start,r.end,BlockPos.ZERO)));assertEquals(trace,events);
        var position=new BlockPos(0,0,0){@Override public int getX(){events.add("posX");return super.getX();}};
        events.clear();expected=JavaVoxelRaycast.clip(new CubeVoxelShape(g),r.start,r.end,position);trace=List.copyOf(events);events.clear();
        var positionHit=new CubeVoxelShape(g).clip(r.start,r.end,position);var positionTrace=List.copyOf(events);
        assertArrayEquals(raw(expected),raw(positionHit));assertEquals(trace,positionTrace);
        var point=new Vec3(-1,.3,.6){@Override public Vec3 add(double x,double y,double z){events.add("add");return super.add(x,y,z);}};
        events.clear();expected=JavaVoxelRaycast.clip(new CubeVoxelShape(g),point,r.end,BlockPos.ZERO);trace=List.copyOf(events);events.clear();
        assertArrayEquals(raw(expected),raw(new CubeVoxelShape(g).clip(point,r.end,BlockPos.ZERO)));assertEquals(trace,events);
        assertNull(Shapes.empty().clip(null,null,null));
        assertThrows(NullPointerException.class,()->s.clip(null,r.end,BlockPos.ZERO));
        var failure=new IllegalStateException("coordinate failure");
        var bad=new AbstractDoubleList(){public int size(){return 9;}public double getDouble(int i){throw failure;}};
        var invalid=new ArrayVoxelShape(g,bad,bad,bad);
        assertSame(failure,assertThrows(IllegalStateException.class,()->JavaVoxelRaycast.clip(invalid,r.start,r.end,BlockPos.ZERO)));
        assertSame(failure,assertThrows(IllegalStateException.class,()->invalid.clip(r.start,r.end,BlockPos.ZERO)));
        compare(new CubeVoxelShape(g).move(.25,-.5,1),r,BlockPos.ZERO,false);
        var nestedShape=new CubeVoxelShape(NativeVoxelBoxesTest.grid(8,8,8,71,"full"));
        var nested=new AbstractDoubleList(){public int size(){return 9;}public double getDouble(int i){
            assertArrayEquals(raw(JavaVoxelRaycast.clip(nestedShape,r.start,r.end,BlockPos.ZERO)),
                raw(nestedShape.clip(r.start,r.end,BlockPos.ZERO)));return i/8.;}};
        compare(new ArrayVoxelShape(g,nested,nested,nested),r,BlockPos.ZERO,false);
    }

    @Test void boundaryDimensionsAndSnapshotCapacityEffects() {
        for(int[] d:new int[][]{{0,8,8},{8,0,8},{8,8,0},{7,8,9},{8,8,8},{257,1,2},{256,256,2}}) {
            var g=NativeVoxelBoxesTest.grid(d[0],d[1],d[2],71,"random");
            compare(NativeVoxelBoxesTest.shape(g),rays().getFirst(),BlockPos.ZERO,false);
        }
        // BitSet.clone may trim source capacity; both calls must leave the same capacity.
        var a=new BitSetDiscreteVoxelShape(8,8,8);a.storage.set(65535);a.storage.clear(65535);a.fill(4,4,4);
        var b=new BitSetDiscreteVoxelShape(8,8,8);b.storage.set(65535);b.storage.clear(65535);b.fill(4,4,4);
        var sa=new CubeVoxelShape(a);var sb=new CubeVoxelShape(b);var r=rays().getFirst();
        assertEquals(a.storage.size(),b.storage.size());
        assertArrayEquals(raw(JavaVoxelRaycast.clip(sa,r.start,r.end,BlockPos.ZERO)),raw(sb.clip(r.start,r.end,BlockPos.ZERO)));
        assertEquals(a.storage.size(),b.storage.size());assertArrayEquals(a.storage.toLongArray(),b.storage.toLongArray());
    }

    @Test void concurrentReadersGcAndIndependentResultOwnership() throws Exception {
        var running=new java.util.concurrent.atomic.AtomicBoolean(true);
        var gc=new Thread(()->{while(running.get()){System.gc();Thread.yield();}});gc.setDaemon(true);gc.start();
        try(var pool=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Future<?>>();
            for(int worker=0;worker<4;worker++){final int seed=worker;tasks.add(pool.submit(()->{
                var s=NativeVoxelBoxesTest.shape(NativeVoxelBoxesTest.grid(16,16,16,seed,"random"));
                for(var ray:rays())compare(s,ray,BlockPos.ZERO,true);
            }));}
            for(var task:tasks)task.get();
        } finally {running.set(false);gc.join(10000);}
        assertFalse(gc.isAlive());
        var s=new CubeVoxelShape(NativeVoxelBoxesTest.grid(8,8,8,71,"full"));
        var result=s.clip(rays().getFirst().start,rays().getFirst().end,BlockPos.ZERO);var before=raw(result);
        s.clip(rays().get(1).start,rays().get(1).end,BlockPos.ZERO);assertArrayEquals(before,raw(result));
        var position=new BlockPos.MutableBlockPos(41,75,-33);var ray=translate(rays().getFirst(),position);
        result=s.clip(ray.start,ray.end,position);assertSame(position,result.getBlockPos());
        position.set(42,76,-34);assertEquals(42,result.getBlockPos().getX());
    }
    @Test void outsideAxisProofMatchesOriginalBinarySearchAndCubeFloor() throws Exception {
        var method=NativeVoxelRaycast.class.getDeclaredMethod("outside",it.unimi.dsi.fastutil.doubles.DoubleList.class,int.class,double.class);
        method.setAccessible(true);
        double[] values={Double.NEGATIVE_INFINITY,-Double.MAX_VALUE,-1.,-Double.MIN_VALUE,-0.,0.,
            Double.MIN_VALUE,.125,.5,1.,Double.MAX_VALUE,Double.POSITIVE_INFINITY,Double.NaN};
        var random=new Random(97177);
        var grid=NativeVoxelBoxesTest.grid(8,8,8,71,"random");
        for(int fixture=0;fixture<512;fixture++) {
            double[] coordinates=new double[9];
            for(int i=0;i<9;i++)coordinates[i]=values[random.nextInt(values.length)];
            var list=DoubleArrayList.wrap(coordinates);
            var shape=new ArrayVoxelShape(grid,list,list,list);
            for(double value:values) if((boolean)method.invoke(null,list,8,value)) {
                int index=shape.findIndex(Direction.Axis.X,value);
                assertTrue(index<0||index>=8,"Outside proof admitted an inside index");
            }
        }
        var cube=new CubeVoxelShape(grid);
        var range=new CubePointRange(8);
        var array=new ArrayVoxelShape(grid,range,range,range);
        for(double value:values) if((boolean)method.invoke(null,range,8,value)) {
            assertTrue(cube.findIndex(Direction.Axis.X,value)<0||cube.findIndex(Direction.Axis.X,value)>=8);
            assertTrue(array.findIndex(Direction.Axis.X,value)<0||array.findIndex(Direction.Axis.X,value)>=8);
        }
    }

}
