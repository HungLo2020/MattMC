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
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVoxelClosestPointTest {
    private static long comparisons;
    @BeforeAll static void bootstrap() { NativeVoxelBoxesTest.bootstrap(); }
    static long[] raw(Optional<Vec3> point) {
        return point.isEmpty() ? new long[0] : new long[]{Double.doubleToRawLongBits(point.get().x),
            Double.doubleToRawLongBits(point.get().y),Double.doubleToRawLongBits(point.get().z)};
    }
    static void compare(VoxelShape shape, Vec3 point, boolean nativeRequired) {
        var expected = JavaVoxelClosestPoint.closestPointTo(shape,point);
        if (shape.getClass()==ArrayVoxelShape.class || shape.getClass()==CubeVoxelShape.class)
            assertArrayEquals(raw(JavaVoxelClosestPoint.vanilla(shape,point)),raw(expected),"existing box stack");
        var direct = NativeVoxelClosestPoint.find(shape,point);
        if (nativeRequired) assertNotNull(direct,"native eligibility");
        if (direct!=null) assertArrayEquals(raw(expected),raw(direct),"direct native");
        assertArrayEquals(raw(expected),raw(shape.closestPointTo(point)),"public caller");
        comparisons++;
    }
    static List<Vec3> probes() {
        return List.of(new Vec3(-1,.25,.75),new Vec3(.5,.5,.5),new Vec3(2,2,-1),
            new Vec3(0,0,0),new Vec3(1,1,1),new Vec3(.499999999999,.500000000001,.875),
            new Vec3(-0.0,Double.MIN_VALUE,-Double.MIN_VALUE),new Vec3(Double.MAX_VALUE,-Double.MAX_VALUE,0));
    }
    @Test void exhaustiveSmallAndComplexCurrentInputs() {
        for (int mask=0;mask<4096;mask++) {
            var g=new BitSetDiscreteVoxelShape(2,2,3);
            for(int i=0;i<12;i++) if((mask&(1<<i))!=0)g.fill(i/6,(i/3)%2,i%3);
            compare(NativeVoxelBoxesTest.shape(g),new Vec3(.5,.5,.5),false);
        }
        int fixtures=0;
        for(int[] d:new int[][]{{8,8,8},{16,16,16},{17,9,7},{3,3,65},{8,1,64},{1,256,256},{256,1,256},{32,8,16}})
            for(String pattern:List.of("empty","full","random","sparse","dense","checker","shell","slabs"))
                for(int seed=0;seed<3;seed++) {
                    var g=NativeVoxelBoxesTest.grid(d[0],d[1],d[2],1977L+seed*137L,pattern);
                    var before=g.storage.toLongArray();int capacity=g.storage.size();
                    for(VoxelShape s:List.of(NativeVoxelBoxesTest.shape(g),new CubeVoxelShape(g)))
                        for(var q:probes())compare(s,q,true);
                    assertArrayEquals(before,g.storage.toLongArray());assertEquals(capacity,g.storage.size());fixtures++;
                }
        System.out.println("CLOSEST_COMPLEX_PARITY fixtures="+fixtures+" comparisons="+comparisons);
    }
    @Test void everyRegisteredCollisionShapeAndDerivedJoins() {
        int states=0,eligible=0,joined=0;
        var probe=NativeVoxelBoxesTest.shape(NativeVoxelBoxesTest.grid(8,8,8,71,"shell"));
        for(var state:Block.BLOCK_STATE_REGISTRY) {
            var s=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
            compare(s,new Vec3(.5,.5,.5),false);compare(s,new Vec3(-.1,.75,1.1),false);states++;
            if(!s.isEmpty()&&NativeVoxelClosestPoint.find(s,new Vec3(.5,.5,.5))!=null)eligible++;
            if(states%47==0&&!s.isEmpty()) {
                var j=Shapes.joinUnoptimized(s,probe,BooleanOp.OR);
                for(var q:probes())compare(j,q,false);joined++;
            }
        }
        assertTrue(eligible>0);
        System.out.println("CLOSEST_REGISTERED_PARITY states="+states+" native_states="+eligible+" joined="+joined+" comparisons="+comparisons);
    }
    @Test void signedZerosTiesReversedCoordinatesAndDistanceOverflow() {
        var g=NativeVoxelBoxesTest.grid(8,8,8,71,"checker");
        double[] c={0.,-0.,Double.MIN_VALUE,0.25,0.5,0.5,0.75,1.,1.};
        var s=new ArrayVoxelShape(g,c,c,c);
        for(var q:probes())compare(s,q,true);
        var reversed=new double[]{3,2,1,0,-1,-2,-3,-4,-5};
        s=new ArrayVoxelShape(g,reversed,c,reversed);
        for(var q:probes())compare(s,q,true);
        // First greedy box must win even when every squared distance overflows.
        compare(s,new Vec3(Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE),true);
        var isolated=new BitSetDiscreteVoxelShape(8,8,8);isolated.fill(0,0,0);isolated.fill(0,0,2);
        var tie=new CubeVoxelShape(isolated);
        compare(tie,new Vec3(.0625,.0625,.1875),true);
        assertEquals(.125,tie.closestPointTo(new Vec3(.0625,.0625,.1875)).orElseThrow().z);
    }
    @Test void everyMixedArrayAndCubeCoordinateLayout() {
        var g=NativeVoxelBoxesTest.grid(8,9,8,71,"checker");
        for(int mask=0;mask<8;mask++) {
            var x=(mask&1)!=0?new CubePointRange(8):NativeVoxelJoinTest.coords(8,0);
            var y=(mask&2)!=0?new CubePointRange(9):NativeVoxelJoinTest.coords(9,0);
            var z=(mask&4)!=0?new CubePointRange(8):NativeVoxelJoinTest.coords(8,0);
            var s=new ArrayVoxelShape(g,x,y,z);
            for(var q:probes())compare(s,q,true);
        }
    }
    @Test void ieeeDistanceTiesUnderflowLargeCoordinatesAndRandomQueryBits() {
        var g=NativeVoxelBoxesTest.grid(8,8,8,71,"checker");
        var standard=NativeVoxelBoxesTest.shape(g);
        compare(standard,new Vec3(1e16,.25,.75),true);
        var random=new Random(1977);
        for(int i=0;i<1024;i++) {
            var q=new Vec3(Math.scalb(random.nextDouble()-.5,random.nextInt(2046)-1022),
                Math.scalb(random.nextDouble()-.5,random.nextInt(2046)-1022),
                Math.scalb(random.nextDouble()-.5,random.nextInt(2046)-1022));
            compare(standard,q,true);
        }
        for(double scale:new double[]{1e-170,1e-160,1e-150,1e150}) {
            var c=new double[9];for(int i=0;i<9;i++)c[i]=(i-4)*scale;
            var s=new ArrayVoxelShape(g,c,c,c);
            for(double factor:new double[]{-10,-2.5,0,.5,2.5,10})compare(s,new Vec3(factor*scale,.3*scale,.7*scale),true);
        }
    }
    @Test void cuboidProofChecksOccupancyRatherThanTrustingBounds() {
        for(int n:new int[]{8,16,32}) {
            var g=new BitSetDiscreteVoxelShape(n,n,n);
            for(int x=1;x<n-1;x++)for(int y=0;y<n/2;y++)for(int z=0;z<n;z++)g.fill(x,y,z);
            var s=NativeVoxelBoxesTest.shape(g);for(var q:probes())compare(s,q,true);
        }
        // Check both row-wise and full-Z contiguous proofs with shifted Y
        // bounds, word boundaries, and equal-cardinality holes/outside cells.
        for (int[] d : new int[][]{{8, 9, 8}, {17, 9, 7}, {3, 3, 65}}) {
            for (boolean fullZ : new boolean[]{false, true}) {
                int x0 = 1, x1 = d[0], y0 = 1, y1 = d[1] - 1;
                int z0 = fullZ ? 0 : 1, z1 = fullZ ? d[2] : d[2] - 1;
                var box = new BitSetDiscreteVoxelShape(d[0], d[1], d[2]);
                for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++)
                    for (int z = z0; z < z1; z++) box.fill(x, y, z);
                var shape = NativeVoxelBoxesTest.shape(box);
                for (var point : probes()) compare(shape, point, true);
                for (int[] hole : new int[][]{{x0, y0, z0}, {x0, y1 - 1, z1 - 1}, {x1 - 1, y1 - 1, z1 - 1}}) {
                    int index = box.getIndex(hole[0], hole[1], hole[2]);
                    box.storage.clear(index);
                    box.storage.set(0); // Bounds and cardinality still describe the old cuboid.
                    for (var point : probes()) compare(shape, point, true);
                    box.storage.clear(0);
                    box.storage.set(index);
                }
            }
        }
        var g=new BitSetDiscreteVoxelShape(8,8,8);g.fill(4,4,4);
        var s=NativeVoxelBoxesTest.shape(g);var q=new Vec3(.5,.5,.5);compare(s,q,true);
        // Equal volume/count, but the actual cell is outside the hinted box.
        g.xMin=1;g.xMax=2;g.yMin=1;g.yMax=2;g.zMin=1;g.zMax=2;compare(s,q,true);
        g.xMin=Integer.MAX_VALUE;g.xMax=Integer.MIN_VALUE;compare(s,q,true);
        g.xMin=4;g.xMax=5;g.yMin=4;g.yMax=5;g.zMin=4;g.zMax=5;
        g.storage.set(65535);compare(s,q,true); // Out-of-grid storage rejects the count proof.
    }
    @Test void nonfiniteCoordinatesAndQueriesRetainRawJavaBits() {
        var g=NativeVoxelBoxesTest.grid(8,8,8,71,"checker");
        for(double value:new double[]{Double.NaN,Double.longBitsToDouble(0x7ff8000000000001L),
            Double.longBitsToDouble(0xfff8000000000002L),Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            var c=new double[]{0,.125,.25,.375,.5,.625,.75,.875,1}; c[4]=value;
            var s=new ArrayVoxelShape(g,c,c,c);
            assertNull(NativeVoxelClosestPoint.find(s,new Vec3(.5,.5,.5)));
            for(var q:probes())compare(s,q,false);
            var regular=new CubeVoxelShape(g);
            compare(regular,new Vec3(value,.5,.5),false);
            for(int index:new int[]{0,4,8}) {
                var full=NativeVoxelBoxesTest.grid(8,8,8,71,"full");
                var a=new double[]{0,.125,.25,.375,.5,.625,.75,.875,1};a[index]=value;
                compare(new ArrayVoxelShape(full,a,a,a),new Vec3(.5,.5,.5),false);
            }
        }
    }
    @Test void customReadsOverridesExceptionsAndReentryStayInOriginalOrder() {
        var events=new ArrayList<String>(); var g=NativeVoxelBoxesTest.grid(8,8,8,71,"checker");
        var coords=new AbstractDoubleList() {
            public int size(){events.add("size");return 9;}
            public double getDouble(int i){events.add("get:"+i);return i/8.;}
        };
        var s=new ArrayVoxelShape(g,coords,coords,coords);var q=new Vec3(.5,.5,.5);events.clear();
        var expected=JavaVoxelClosestPoint.closestPointTo(s,q);var trace=List.copyOf(events);events.clear();
        assertArrayEquals(raw(expected),raw(s.closestPointTo(q)));assertEquals(trace,events);
        var special=new VoxelShape(g) {
            public it.unimi.dsi.fastutil.doubles.DoubleList getCoords(Direction.Axis axis){throw new AssertionError("coords");}
            public void forAllBoxes(Shapes.DoubleLineConsumer consumer){events.add("override");consumer.consume(1,2,3,4,5,6);}
        };
        events.clear();expected=JavaVoxelClosestPoint.closestPointTo(special,q);trace=List.copyOf(events);events.clear();
        assertArrayEquals(raw(expected),raw(special.closestPointTo(q)));assertEquals(trace,events);
        var failure=new IllegalStateException("coordinate failure");
        var failing=new AbstractDoubleList(){public int size(){return 9;}public double getDouble(int i){throw failure;}};
        var invalid=new ArrayVoxelShape(g,failing,failing,failing);
        assertSame(failure,assertThrows(IllegalStateException.class,()->JavaVoxelClosestPoint.closestPointTo(invalid,q)));
        assertSame(failure,assertThrows(IllegalStateException.class,()->invalid.closestPointTo(q)));
        var regular=new CubeVoxelShape(g);
        var nested=new AbstractDoubleList(){public int size(){return 9;}public double getDouble(int i){assertNotNull(regular.closestPointTo(q));return i/8.;}};
        compare(new ArrayVoxelShape(g,nested,nested,nested),q,false);
        assertThrows(NullPointerException.class,()->regular.closestPointTo(null));
        assertEquals(Optional.empty(),Shapes.empty().closestPointTo(null));
        var customPoint=new Vec3(.5,.5,.5) {
            @Override public double distanceToSqr(double a,double b,double c){events.add("distance");return super.distanceToSqr(a,b,c);}
        };
        assertNull(NativeVoxelClosestPoint.find(regular,customPoint));
        events.clear();expected=JavaVoxelClosestPoint.closestPointTo(regular,customPoint);trace=List.copyOf(events);events.clear();
        assertArrayEquals(raw(expected),raw(regular.closestPointTo(customPoint)));assertEquals(trace,events);
        var nullGrid=new VoxelShape(null) {
            @Override public boolean isEmpty(){return false;}
            public it.unimi.dsi.fastutil.doubles.DoubleList getCoords(Direction.Axis a){throw new AssertionError("coords");}
            @Override public void forAllBoxes(Shapes.DoubleLineConsumer c){c.consume(1,2,3,4,5,6);}
        };
        compare(nullGrid,q,false);
        // Unsupported moved/custom coordinate lists keep their virtual path.
        compare(regular.move(.25,-.5,1),q,false);
    }
    @Test void boundarySizesPaddingStaleBoundsAndChangesAreFresh() {
        for(int[] d:new int[][]{{0,8,8},{8,0,8},{8,8,0},{7,8,9},{8,8,8},{257,1,2},{256,256,2}})
            compare(NativeVoxelBoxesTest.shape(NativeVoxelBoxesTest.grid(d[0],d[1],d[2],71,"random")),new Vec3(.5,.5,.5),false);
        var g=NativeVoxelBoxesTest.grid(17,9,7,71,"random");g.storage.set(1071,1100);g.xMin=-77;g.zMax=Integer.MAX_VALUE;
        var s=NativeVoxelBoxesTest.shape(g);var q=new Vec3(.5,.5,.5);compare(s,q,true);
        g.storage.set(65537);compare(s,q,false);assertNull(NativeVoxelClosestPoint.find(s,q));g.storage.clear(65537);
        var outside=new BitSetDiscreteVoxelShape(8,8,8);outside.storage.set(513);
        var outsideShape=new CubeVoxelShape(outside);
        assertThrows(NullPointerException.class,()->JavaVoxelClosestPoint.closestPointTo(outsideShape,q));
        assertThrows(NullPointerException.class,()->outsideShape.closestPointTo(q));
        for(int i=0;i<20;i++) {g.storage.flip(0,1071);compare(s,q,true);}
        var c=DoubleArrayList.wrap(new double[]{0,.125,.25,.375,.5,.625,.75,.875,1});
        var current=new ArrayVoxelShape(NativeVoxelBoxesTest.grid(8,8,8,71,"random"),c,c,c);
        for(int i=0;i<20;i++){c.set(4,.4+i*.005);compare(current,q,true);}
    }
    @Test void concurrentCallsGcAndIndependentOwnedResults() throws Exception {
        var running=new java.util.concurrent.atomic.AtomicBoolean(true);
        var gc=new Thread(()->{while(running.get()){System.gc();Thread.yield();}});gc.setDaemon(true);gc.start();
        try(var pool=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Future<?>>();
            for(int worker=0;worker<4;worker++) {final int seed=worker;tasks.add(pool.submit(()->{
                var s=NativeVoxelBoxesTest.shape(NativeVoxelBoxesTest.grid(16,16,16,seed,"random"));
                for(int i=0;i<20;i++){var q=new Vec3(i*.05,.37,.68);compare(s,q,true);}
            }));}
            for(var t:tasks)t.get();
        } finally {running.set(false);gc.join(10000);}
        assertFalse(gc.isAlive());
        var s=new CubeVoxelShape(NativeVoxelBoxesTest.grid(8,8,8,71,"checker"));
        var p=s.closestPointTo(new Vec3(-1,.37,.68)).orElseThrow();var before=raw(Optional.of(p));
        s.closestPointTo(new Vec3(2,-1,2));assertArrayEquals(before,raw(Optional.of(p)));
    }
}
